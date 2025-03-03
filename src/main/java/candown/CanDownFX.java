package candown;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import js.Wrapper;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;

/**
 * Application to view markdown files. It is possible to set auto-refresh which
 * updates the view if the file changes.
 *
 * @author Peter Hull
 */
public class CanDownFX extends Application {

    /// How often to check for file changes (ms)
    private static final int REFRESH_INTERVAL = 2500;
    /// Key for the SOURCE_FILE property of a tab.
    private static final Object SOURCE_FILE = new Object();
    /// Key for the UPDATE_TIME property of a tab.
    private static final Object UPDATE_TIME = new Object();
    private static final int MRU_MAX_SIZE = 5;
    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
    /// The chooser used to open files
    private final FileChooser chooser = new FileChooser();
    /// A timer used for auto refresh events
    private final Timer autorefreshTimer = new Timer("autorefreshTimer", true);
    private final Renderer renderer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /// Reference to the tab pane Node
    private TabPane tabPane;
    private Stage mainStage;
    /// Current timer task or null
    private RefreshTask refreshTask;
    private ObservableList<MenuItem> recentFiles;

    /**
     * Create a new instance of this app.
     */
    public CanDownFX() {
        Wrapper wrapper = new Wrapper();
        this.renderer = wrapper.getRenderer();
    }


    /**
     * Start the application. This sets up the UI and adds in the event
     * handlers.
     *
     * @param primaryStage the stage which is the main one for the application.
     */
    @Override
    public void start(Stage primaryStage) {
        // store the main stage for later.
        this.mainStage = primaryStage;
        // Set app icon
        primaryStage.getIcons().addAll(Stream.of("star16.png", "star32.png", "star48.png", "star64.png")
                .map(s -> getClass().getResource(s).toString())
                .map(Image::new)
                .toList());

        // Create all menu items.
        Menu fileMenu = new Menu("File");
        MenuItem fileOpenMenu = new MenuItem("Open...");
        MenuItem fileExportMenu = new MenuItem("Export...");
        Menu fileRecentMenu = new Menu("Recent");
        MenuItem fileExitMenu = new MenuItem("Exit");
        Menu viewMenu = new Menu("View");
        MenuItem viewRefreshMenu = new MenuItem("Refresh");
        CheckMenuItem viewAutoRefreshMenu = new CheckMenuItem("Auto Refresh");

        // Assemble the menu bar
        fileMenu.getItems().addAll(fileOpenMenu, fileExportMenu, fileRecentMenu, new SeparatorMenuItem(), fileExitMenu);
        viewMenu.getItems().addAll(viewRefreshMenu, viewAutoRefreshMenu);
        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(fileMenu, viewMenu);

        // Bind in the handlers
        fileOpenMenu.setOnAction(this::onOpen);
        fileExportMenu.setOnAction(this::onExport);

        recentFiles = fileRecentMenu.getItems();
        viewAutoRefreshMenu.selectedProperty().addListener((ObservableValue<? extends Boolean> ov, Boolean oldValue, Boolean newValue) -> {
            if (newValue) {
                refreshTask = new RefreshTask();
                autorefreshTimer.schedule(refreshTask, 0, REFRESH_INTERVAL);
            } else {
                refreshTask.cancel();
                refreshTask = null;
            }
        });
        viewRefreshMenu.setOnAction(this::onRefresh);
        fileExitMenu.setOnAction(this::onExit);
        // disable the Refresh menu if auto-refresh is on (because not needed)
        viewRefreshMenu.disableProperty().bind(viewAutoRefreshMenu.selectedProperty());

        // create and store the tab pane
        tabPane = new TabPane();
        // Bind the list of tabs
        fileExportMenu.disableProperty().bind(Bindings.isEmpty(tabPane.getTabs()));

        // Add the menu and tab view to the root pane
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(tabPane);

        // Set up the main (and only) scene.
        Scene scene = new Scene(root, 400, 600);
        primaryStage.setTitle("CanDown - Markdown Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();

        loadMRU();
    }

    private void loadMRU() {
        // Load the preferences
        Preferences prefs = Preferences.userNodeForPackage(this.getClass());
        int n = prefs.getInt("MRU.N", 0);
        for (int i = 0; i < n; ++i) {
            String key = String.format("MRU.%d", i);
            String v = prefs.get(key, null);
            if (v != null) {
                File f = new File(v);
                recentFiles.add(makeRecentItem(f));
            }
        }
    }

    private void onTimerTick() {
        tabPane.getTabs().forEach(this::loadTabContent);
    }

    /**
     * Refresh handler. Called when the View|Refresh menu is chosen.
     *
     * @param event the menu event.
     */
    private void onRefresh(ActionEvent event) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null) {
            loadTabContent(tab);
        }
    }

    /**
     * Exit handler. Called when the File|Exit menu is chosen.
     *
     * @param event the action event (not used)
     */
    private void onExit(ActionEvent event) {
        mainStage.close();
    }

    private void onOpen(ActionEvent event) {
        File f = chooser.showOpenDialog(mainStage);
        if (f != null) {
            f = f.getAbsoluteFile();
            addToRecent(f);
            findOrOpen(f);
        }
    }

    private void findOrOpen(File f) {
        for (Tab t : tabPane.getTabs()) {
            if (t.getProperties().get(SOURCE_FILE).equals(f)) {
                tabPane.getSelectionModel().select(t);
                return;
            }
        }
        openFile(f);
    }

    /**
     * Initialise the application. Do some things that don't need to be on the
     * application thread.
     */
    @Override
    public void init() {
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown", "*.MD"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
    }

    /**
     * Load new file content for a tab. This happens asynchronously. The tab
     * must have had its SOURCE_FILE property set. If the file has not been
     * updated since it was loaded, don't do anything. If there's an error then
     * the content is set to be the exception message.
     * <p>
     * Note: must be called from the application thread.
     *
     * @param tab the tab to load.
     */
    private void loadTabContent(Tab tab) {
        if (tab.getProperties().get(SOURCE_FILE) instanceof File filename) {
            boolean needsReload = true;
            if (tab.getProperties().get(UPDATE_TIME) instanceof Long updateTime) {
                needsReload = updateTime != filename.lastModified();
            }
            if (needsReload) {
                tab.getProperties().put(UPDATE_TIME, filename.lastModified());

                Task<String> reloader = new Task<>() {

                    @Override
                    protected String call() throws Exception {
                        String in = Files.readString(filename.toPath());
                        return renderer.render(in);

                    }

                };
                final WebEngine webEngine = ((WebView) tab.getContent()).getEngine();
                // Succeeded, show the content as html
                reloader.setOnSucceeded((WorkerStateEvent event) -> {
                    Object value = event.getSource().getValue();
                    webEngine.loadContent(Objects.toString(value), "text/html");
                });
                // Failed, show exception as plain text
                reloader.setOnFailed((WorkerStateEvent event) -> webEngine.loadContent(event.getSource().getException().toString(), "text/plain"));
                // Actually do the work on a different thread.
                executor.submit(reloader);
            }
        }
    }

    /**
     * Open a new tab with the given file. This initialises the tab and then
     * defers loading to loadTabContent()
     *
     * @param filename the file to use.
     */
    private void openFile(File filename) {
        Tab tab = new Tab();
        tab.getProperties().put(SOURCE_FILE, filename.getAbsoluteFile());
        WebView wv = new WebView();
        tab.setContent(wv);
        tab.setText(filename.getName());
        wv.setContextMenuEnabled(false);
        WebEngine engine = wv.getEngine();
        // Disable any hyperlinks (taken from https://stackoverflow.com/a/33445383)
        engine.getLoadWorker().stateProperty().addListener((obs, oldval, newval) -> {
            if (Objects.equals(newval, Worker.State.SUCCEEDED)) {
                NodeList anchors = engine.getDocument().getElementsByTagName("a");
                for (int i = 0; i < anchors.getLength(); ++i) {
                    org.w3c.dom.events.EventTarget target = (org.w3c.dom.events.EventTarget) anchors.item(i);
                    target.addEventListener("click", Event::preventDefault, false);
                }
            }
        });
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        loadTabContent(tab);
    }

    /**
     * Stop the application. Shuts down the update timer and the execution
     * queue. Save any preferences
     *
     * @throws Exception if something goes wrong
     */
    @Override
    public void stop() throws Exception {
        autorefreshTimer.cancel();
        executor.shutdownNow();
        //Store preferences
        Preferences prefs = Preferences.userNodeForPackage(this.getClass());
        prefs.putInt("MRU.N", recentFiles.size());
        for (int i = 0; i < recentFiles.size(); ++i) {
            File f = (File) recentFiles.get(i).getUserData();
            prefs.put(String.format("MRU.%d", i), f.getAbsolutePath());
        }
        prefs.sync();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    private void onOpenRecent(ActionEvent event) {
        javafx.event.EventTarget target = event.getTarget();
        if (target instanceof MenuItem m) {
            if (m.getUserData() instanceof File f) {
                findOrOpen(f);
            }
        }
    }

    private void addToRecent(File f) {
        MenuItem found = null;
        for (MenuItem recentFile : recentFiles) {
            if (Objects.equals(f, recentFile.getUserData())) {
                // Found it
                found = recentFile;
                break;
            }
        }
        if (found == null) {
            MenuItem item = makeRecentItem(f);
            if (recentFiles.size() > MRU_MAX_SIZE) {
                recentFiles.remove(recentFiles.size() - 1);
            }
            recentFiles.add(0, item);
        } else {
            // jump the found one up to the top
            recentFiles.remove(found);
            recentFiles.add(0, found);
        }
    }

    private MenuItem makeRecentItem(File f) {
        // Add new
        MenuItem item = new MenuItem(f.getAbsolutePath());
        item.setUserData(f);
        item.setOnAction(this::onOpenRecent);
        return item;
    }

    private void onExport(ActionEvent event) {
        Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
        if (currentTab.getProperties().get(SOURCE_FILE) instanceof File file) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String html = renderer.render(content);
                Files.writeString(Path.of("test.html"), html, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                Logger.getLogger(CanDownFX.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Refresh timer task. Runs onTimerTick(), always on the Application thread.
     */
    private class RefreshTask extends TimerTask {

        @Override
        public void run() {
            Platform.runLater(CanDownFX.this::onTimerTick);
        }
    }

}
