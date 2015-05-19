Introduction
============

I wanted a simple application to view [markdown][] files. This is a
plain-text format which reads naturally as it stands, but also can
readily be converted to HTML.  There are plenty of Java markdown
processors available so I thought I would use the `WebView` component
provided by [JavaFX][] for display. The whole project is just then a
bit of glue between a markdown processor and a display component. I
used a [marked][] (which is a pure javascript solution) and JavaFX 8.0. 
The IDE was [NetBeans] 8.0.2,
though I did not use anything specific to the IDE.  In terms of
specification, I just wanted to be able to view multiple files at
once, and to have the display update automatically if any file changed
on disk.

[markdown]: daringfireball.net/projects/markdown/
[JavaFX]: http://www.oracle.com/technetwork/java/javafx/overview/index.html
[marked]: https://github.com/chjj/marked
[NetBeans]: http://www.netbeans.org/

The User Interface
==================

I set this up as simply as possible. There's a menu bar, then a tab
pane which fills the rest of the window. Each document displays in a
separate tab. Each tab contains a single `WebView` as its content
pane. Details of this are included later.

![Sketch of User Interface](CanDown.png)

I generate the UI with code rather than FXML; probably it would be
better to use the latter for a more complicated interface.

~~~~
// Create all menu items.
Menu fileMenu = new Menu("File");
MenuItem fileOpenMenu = new MenuItem("Open...");
MenuItem fileExitMenu = new MenuItem("Exit");
Menu viewMenu = new Menu("View");
MenuItem viewRefreshMenu = new MenuItem("Refresh");
CheckMenuItem viewAutoRefreshMenu = new CheckMenuItem("Auto Refresh");

// Assemble the menu bar
fileMenu.getItems().addAll(fileOpenMenu, new SeparatorMenuItem(), fileExitMenu);
viewMenu.getItems().addAll(viewRefreshMenu, viewAutoRefreshMenu);
MenuBar menuBar = new MenuBar();
menuBar.getMenus().addAll(fileMenu, viewMenu);
~~~~

Rendering the Content
=====================

This is straightforward. I set up a reader to open the markdown file
(using UTF-8 encoding.) I created a Java ScriptEngine and
loaded the `marked.js` script into it, then a wrapper which implements the 
(Java) Renderer interface. I then called the `render()` function
to run the conversion. The script only generates the
body text, so I put some HTML tags either side of this to make it into
a 'proper' document.

In Java:

~~~~
public interface Renderer {
  String render(String input);  
}
~~~~

In Javascript:

~~~~
/* implementation of candown.Renderer interface */
function render(s) {
    return "<html><body>"+marked(s, {gfm: true})+"</body</html>";
}
~~~~


Note that there's no error handling at all here. Any exceptions are
caught by the surrounding code (see later) but the `FileReader` could
be left open if there is a parsing error. This should really be
protected with try/finally blocks.

Displaying
==========

Each `WebView` control has an associated `WebEngine`. The `WebEngine`
accepts content in string form; it then processes this and passes the
data onto the view to display.

    final WebEngine webEngine = view.getEngine();
    webEngine.loadContent(content, "text/html");

There's a slight issue with this; `WebView` is a fully-featured
web-browser component, so clicking on an external link will load up an
URL. I implemented a subclass of `HtmlEmitter` to replace any
non-local links with '#', but I do not discuss that here.

Threading
=========

Reading and parsing the file could take some time, so it shouldn't be
done on the FX application thread.

However all updates to controls must be done on the application
thread.  A solution is to use the facilities from javafx.concurrent,
specifically Task.  I create a Task object to read and parse the
markdown file, returning the HTML string. I then add a handler to the
task to respond once the work is done. This sends the content to the
WebEngine.  Converting the HTML for the view is done internally by the
WebView/WebEngine on a different thread, so I don't need to worry
about that.  I also add a failure handler which will catch any
Exceptions thrown by the task. In this case I just tell the WebView to
display the exception as text.  So, the loading, rendering and display
code looks like this.

	Task<String> reloader = new Task<String>() {

                @Override
                protected String call() throws Exception {
                    String in = new String(Files.readAllBytes(filename.toPath()), StandardCharsets.UTF_8);
                    return renderer.render(in);

                }

            };
            final WebEngine webEngine = ((WebView) tab.getContent()).getEngine();
            // Succeeded, show the content as html
            reloader.setOnSucceeded((WorkerStateEvent event) -> {
                final Object value = event.getSource().getValue();
                if (value != null) {
                    webEngine.loadContent(value.toString(), "text/html");
                }
            });
            // Failed, show exception as plain text
            reloader.setOnFailed((WorkerStateEvent event) -> {
                webEngine.loadContent(event.getSource().getException().toString(), "text/plain");
            });
            // Actually do the work on a different thread.
            executor.submit(reloader);

Once the reloader object is set up, it can be run. The simplest way is to start a new thread.

	new Thread(reloader).start();

I actually use an `ExecutorService` from `java.util.concurrent` to
keep control of thread creation. The loading and processing takes
place on a separate thread, then the `Task` mechanism ensures that the
handlers are always called on the application thread.

Displaying a Document
=====================

Combining the previous steps, when the user selects a file, I

* Create a new tab in the tab pane
* Set the File as a property on that tab
* Start a reloader task to get and display the content

I track the File's last modified time, also as a property of the
tab. This is used when refreshing a tab's contents to avoid extra work
if the file hasn't changed.

Setting properties on the Tab itself is a useful way of attaching some
state without having to subclass Tab or maintain a separate look-up
table. I use objects as keys (I could have used Strings instead.)

	private static final Object SOURCE_FILE = new Object();
	private static final Object UPDATE_TIME = new Object();

Then, I can read or write them as follows.

	tab.getProperties().put(SOURCE_FILE, filename.getAbsoluteFile());
	...
	final File filename = (File) tab.getProperties().get(SOURCE_FILE);

My code to create a new tab is as follows.

	private void openFile(File filename) {
		Tab tab = new Tab();
		tab.getProperties().put(SOURCE_FILE, filename.getAbsoluteFile());
		WebView wv = new WebView();
		tab.setContent(wv);
		tab.setText(filename.getName());
		wv.setContextMenuEnabled(false);
		tabPane.getTabs().add(tab);
		tabPane.getSelectionModel().select(tab);
		loadTabContent(tab);
	}

where `loadTabContent()` reads the filename from the tab's properties
and starts the load task as described above. To reload a tab's
contents I just need to call `loadTabContent()` again; it's
self-contained. The function is shown below, omitting the Task
creating code for brevity. This includes code to check if the file's
been modified since it was last loaded.

	private void loadTabContent(Tab tab) {
		final File filename = (File) tab.getProperties().get(SOURCE_FILE);
		Object updateObject = tab.getProperties().get(UPDATE_TIME);
		boolean needsReload = true;
		if (updateObject != null) {
			long updateTime = (Long) updateObject;
			needsReload = updateTime != filename.lastModified();
		}
		if (needsReload) {
			tab.getProperties().put(UPDATE_TIME, filename.lastModified());
			Task<String> reloader = ...;
			// Actually do the work on a different thread.
			executor.submit(reloader);
		}
	}

Note that `UPDATE_TIME` is `null` the first time around so I check for
that, and force an update if it is not set.

Refresh and Auto-refresh
========================

Given the `loadTabContent` method above, implementing the refresh menu
item is easy.

	private void onRefresh(ActionEvent t) {
		Tab tab = tabPane.getSelectionModel().getSelectedItem();
		if (tab != null) {
			loadTabContent(tab);
		}
	}

I just check that `tab` isn't `null` in case the user selects refresh
when no tabs are open.

For auto-refresh, I set up a timer to periodically try to reload all
the open tabs. Because the file update time is compared, if the tab
doesn't need to be reloaded then not much extra work is done. This
could be done with `java.nio.file.WatchService` but it seemed
unnecessary for this application.

JavaFX doesn't seem to have the equivalent of `javax.swing.Timer`
(maybe there's something in the javafx.animation package), so I used
`java.util.Timer` instead.

	private Timer autorefreshTimer = new Timer("autorefreshTimer", true);

As always there is a little bit of trickery to get the events to
happen on the FX application thread.

	private class RefreshTask extends TimerTask {
		@Override
		public void run() {
			if (Platform.isFxApplicationThread()) {
				onTimerTick();
			} else {
				Platform.runLater(this);
			}
		}
	}
    ...
    autorefreshTimer.schedule(new RefreshTask(), 0, REFRESH_INTERVAL);

When the timer fires, it calls the `run()` method on its *own* thread.
The method realises it is not on the application thread and
reschedules itself to run on the correct thread. This could also have
been written as follows.

	public void run() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				onTimerTick();
			}
		});
	}

I preferred not to have the nested `Runnable`s.

When the timer ticks, it is again simple to refresh all tabs, now I
know it's always going to be on the Application thread.

	private void onTimerTick() {
		for (Tab tab : tabPane.getTabs()) {
			loadTabContent(tab);
		}
	}

Binding the User Interface
==========================

I set actions for each menu item using code like the following.

	fileOpenMenu.setOnAction(new EventHandler<ActionEvent>() {
		@Override
		public void handle(ActionEvent t) {
			onOpen(t);
		}
	});

This just connects each one to a 'top-level' method in the Application
class (`onOpen` in this case.)  I did this to separate the actual
working code from the handler mechanism.

I wanted the Refresh menu to be unavailable if Auto-refresh was
active, since it wouldn't do anything useful. This didn't need a
handler, just a property bind.

	viewRefreshMenu.disableProperty().bind(viewAutoRefreshMenu.selectedProperty());

Conclusion
==========

The implementation was pretty straightforward; the main complication
was making sure the correct thread was used for each activity. Future
work could include

* Better error handling.
* Use FXML.
* Implement a recently-used file list.

Full source code is [available][1].

[1]: http://bitbucket.org/peterhull90/candownfx



