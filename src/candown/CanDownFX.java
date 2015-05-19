package candown;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import js.Wrapper;

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
    /// Reference to the tab pane Node
    private TabPane tabPane;
    /// The chooser used to open files
    private final FileChooser chooser = new FileChooser();
    /// A timer used for auto refresh events
    private final Timer autorefreshTimer = new Timer("autorefreshTimer", true);
    private final Renderer renderer;

    private Scene scene;
    private Stage mainStage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /// Current timer task or null
    private RefreshTask refreshTask;

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
                .map(u -> new Image(u))
                .collect(Collectors.toList()));

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

        // Bind in the handlers
        fileOpenMenu.setOnAction(this::onOpen);
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

        // Add the menu and tab view to the root pane
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(tabPane);

        // Set up the main (and only) scene.
        scene = new Scene(root, 300, 250);
        primaryStage.setTitle("CanDown - Markdown Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void onTimerTick() {
        for (Tab tab : tabPane.getTabs()) {
            loadTabContent(tab);
        }
    }

    /**
     * Refresh handler. Called when the View|Refresh menu is chosen.
     *
     * @param t the menu event.
     */
    private void onRefresh(ActionEvent t) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null) {
            loadTabContent(tab);
        }
    }

    /**
     * Exit handler. Called when the File|Exit menu is chosen.
     *
     * @param event
     */
    private void onExit(ActionEvent event) {
        mainStage.close();
    }

    private void onOpen(ActionEvent event) {
        File f = chooser.showOpenDialog(mainStage);
        if (f != null) {
            f = f.getAbsoluteFile();
            boolean found = false;
            for (Tab t : tabPane.getTabs()) {
                if (t.getProperties().get(SOURCE_FILE).equals(f)) {
                    tabPane.getSelectionModel().select(t);
                    found = true;
                    break;
                }
            }
            if (!found) {
                openFile(f);
            }
        }
    }

    /**
     * Initialise the application. Do some things that don't need to be on the
     * application thread.
     *
     * @throws Exception
     */
    @Override
    public void init() throws Exception {
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md", "*.markdown", "*.MD"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
    }

    /**
     * Load new file content for a tab. This happens asynchronously. The tab
     * must have had its SOURCE_FILE property set. If the file has not been
     * updated since it was loaded, don't do anything. If there's an error then
     * the content is set to be the exception message.
     *
     * @note: must be called from the application thread.
     * @param tab the tab to load.
     */
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
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        loadTabContent(tab);
    }

    /**
     * Stop the application. Shuts down the update timer and the execution
     * queue.
     *
     * @throws Exception
     */
    @Override
    public void stop() throws Exception {
        autorefreshTimer.cancel();
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
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
