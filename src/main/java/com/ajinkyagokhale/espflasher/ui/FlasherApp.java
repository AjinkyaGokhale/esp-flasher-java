package com.ajinkyagokhale.espflasher.ui;

import com.ajinkyagokhale.espflasher.listener.FlashListener;
import com.ajinkyagokhale.espflasher.listener.PortListener;
import com.ajinkyagokhale.espflasher.model.FlashConfig;
import com.ajinkyagokhale.espflasher.service.EsptoolRunner;
import com.ajinkyagokhale.espflasher.service.PortWatcher;
import com.ajinkyagokhale.espflasher.service.PrereqChecker;
import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javafx.scene.media.AudioClip;

import javax.sound.sampled.Port;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class FlasherApp extends Application implements FlashListener, PortListener {
    // UI Controls
    private TextField binPathField;
    private ComboBox<String> chipCombo;
    private ComboBox<String> portCombo;
    private ComboBox<String> baudCombo;
    private TextField flashOffsetField;
    private ProgressBar progressBar;
    private TextArea logArea;
    private Button flashButton;
    private Button stopButton;
    private Button factoryButton;
    private Label statusLabel;

    // Services
    private EsptoolRunner esptoolRunner;
    private PortWatcher portWatcher;
    private PrereqChecker prereqChecker;

    //audio clips
    private AudioClip successSound;
    private AudioClip failSound;

    //flashcounter
    private int flashCount = 0;
    private Label flashCountLabel;
    private boolean isFactoryMode = false;



    @Override
    public void onProgress(int percent) {
        Platform.runLater(() -> {
            progressBar.setProgress(percent / 100.0);
        });
    }

    @Override
    public void onLog(String line) {
        Platform.runLater(() -> {
            logArea.appendText(line + "\n");
        });

    }

    @Override
    public void onComplete(boolean success, String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            flashButton.setDisable(false);
            factoryButton.setDisable(false);
            stopButton.setDisable(true);
            if (success) {
                flashCount++;
                flashCountLabel.setText("Flashed: " + flashCount);
                progressBar.setStyle("-fx-accent: green;");
                progressBar.setProgress(1.0);
                successSound.play();
                statusLabel.setStyle("-fx-text-fill: green;");
            } else {
                failSound.play();
                progressBar.setStyle("-fx-accent: red;");
                progressBar.setProgress(1.0);
                statusLabel.setStyle("-fx-text-fill: red;");
            }

            if (isFactoryMode) {
                statusLabel.setText("Waiting for next device...");
                statusLabel.setStyle("-fx-text-fill: #66bb6a;");
                flashButton.setDisable(true);
                stopButton.setDisable(false);
            }
        });
    }

    @Override
    public void onNewPort(String portName) {
        Platform.runLater(() -> {
            statusLabel.setText("Device detected: " + portName + " — flashing...");
            startFlash(portName);
        });
    }

    private void showPythonMissingDialog() {
        flashButton.setDisable(true);
        factoryButton.setDisable(true);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Python Not Found");
        alert.setHeaderText("Python is required to run esptool");
        alert.setContentText("Please install Python 3, then restart this app.");

        ButtonType downloadBtn = new ButtonType("Download Python");
        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(downloadBtn, closeBtn);

        alert.showAndWait().ifPresent(btn -> {
            if (btn == downloadBtn) {
                getHostServices().showDocument("https://python.org");
            }
        });

        statusLabel.setText("⚠ Python not found — install Python and restart.");
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    private void autoInstallEsptool() {
        flashButton.setDisable(true);
        factoryButton.setDisable(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Installing esptool, please wait...");

        Thread thread = new Thread(() -> {
            boolean success = prereqChecker.installEsptool();

            Platform.runLater(() -> {
                progressBar.setProgress(0);
                if (success) {
                    statusLabel.setText("Ready.");
                    statusLabel.setStyle("");
                    flashButton.setDisable(false);
                    factoryButton.setDisable(false);
                    logArea.appendText("✓ esptool installed successfully.\n");
                } else {
                    statusLabel.setText("⚠ esptool install failed — check log.");
                    logArea.appendText("✗ esptool install failed.\n");
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void startFlash(String overridePort) {

        //guard
        if (!prereqChecker.isReady()) {
            statusLabel.setText("esptool not found. Please install it first.");
            return;
        }
        //factory mode
        String port = overridePort != null ? overridePort : portCombo.getValue();

        // 1. validate inputs
        String binPath = binPathField.getText();
        if (binPath.isEmpty()) {
            statusLabel.setText("Please select a firmware file.");
            return;
        }

        //String port = portCombo.getValue();
        if (port == null || port.isEmpty()) {
            statusLabel.setText("Please select a port.");
            return;
        }

        // 2. build FlashConfig
        FlashConfig config = new FlashConfig(
                chipCombo.getValue(),
                Integer.parseInt(baudCombo.getValue()),
                port,
                binPath,
                flashOffsetField.getText(),
                prereqChecker.getEsptoolCmd()
        );

        // 3. update UI
        flashButton.setDisable(true);
        stopButton.setDisable(false);
        progressBar.setStyle("");
        progressBar.setProgress(0);
        statusLabel.setText("Flashing...");

        // 4. start flashing
        esptoolRunner.startFlashing(config, this);
    }

    private void startFactoryMode() {

        if (binPathField.getText().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Firmware Selected");
            alert.setHeaderText(null);
            alert.setContentText("Please select a firmware .bin file before starting factory mode.");
            alert.showAndWait();
            return;
        }
        if (!prereqChecker.isReady()) {
            statusLabel.setText("esptool not ready.");
            return;
        }


        isFactoryMode = true;

        flashButton.setDisable(true);
        factoryButton.setText("Stop Factory");
        factoryButton.setOnAction(e -> stopAll());
        stopButton.setDisable(false);
        statusLabel.setText("Factory mode — waiting for device...");
        statusLabel.setStyle("-fx-text-fill: #66bb6a;");



        //confirmation box
        // confirmation dialog
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Start Factory Mode");
        confirm.setHeaderText("Ready to flash?");

// big font bin file name
        Label binLabel = new Label(new File(binPathField.getText()).getName());
        binLabel.getStyleClass().add("confirm-bin-name");

        Label chipLabel = new Label("Chip: " + chipCombo.getValue() + "   Baud: " + baudCombo.getValue());
        chipLabel.getStyleClass().add("confirm-details");

        VBox content = new VBox(8, binLabel, chipLabel);
        confirm.getDialogPane().setContent(content);
        // add this here ↓
        confirm.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm()
        );
        confirm.setGraphic(null);  //
        ButtonType startBtn = new ButtonType("Start Factory", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(startBtn, cancelBtn);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != startBtn) return;  // cancelled


        portWatcher.startWatching(this);
    }

    private void stopAll() {
        portWatcher.stopWatching();
        esptoolRunner.stopFlashing();
        isFactoryMode = false;
        flashButton.setDisable(false);
        factoryButton.setText("Factory Mode");
        factoryButton.setOnAction(e -> startFactoryMode());
        stopButton.setDisable(true);
        statusLabel.setText("Stopped.");
        statusLabel.setStyle("");
    }


    //helper
    private void browseBin(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Firmware");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Binary files (*.bin)", "*.bin")
        );
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            binPathField.setText(file.getAbsolutePath());
        }

    }

    private void refreshPorts() {
        portCombo.getItems().clear();
        List<String> espPorts = PortWatcher.listEsp32Ports();

        if (espPorts.isEmpty()) {
            portCombo.setPromptText("No ESP32 detected...");
        } else {
            portCombo.getItems().addAll(espPorts);
            portCombo.getSelectionModel().selectFirst();  // auto select first ESP32
        }
    }

    private void installEsptool() {
        if (prereqChecker.getPipCmd() == null) {
            statusLabel.setText("pip not found — install Python first.");
            return;
        }

        statusLabel.setText("Installing esptool...");
        statusLabel.setOnMouseClicked(null);

        Thread thread = new Thread(() -> {
            boolean success = prereqChecker.installEsptool();
            Platform.runLater(() -> {
                if (success) {
                    prereqChecker.checkAll();
                    statusLabel.setText("Ready.");
                    statusLabel.setStyle("");
                } else {
                    statusLabel.setText("Install failed — check log.");
                }
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void checkPrerequisites() {
        statusLabel.setText("Checking prerequisites...");

        Thread thread = new Thread(() -> {
            prereqChecker.checkAll();

            Platform.runLater(() -> {
                if (prereqChecker.isReady()) {
                    statusLabel.setText("Ready.");
                } else {
                    statusLabel.setText("esptool not found — click here to install.");
                    // we'll add install dialog next
                }
            });
        });
        thread.setDaemon(true);
        thread.start();

        Platform.runLater(() -> {
            if (prereqChecker.isReady()) {
                statusLabel.setText("Ready.");
            } else if (prereqChecker.getPythonCmd() == null) {
                showPythonMissingDialog();
            } else {
                autoInstallEsptool();
            }
        });
    }

    private boolean isDarkMode() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("mac")) {
                Process p = Runtime.getRuntime().exec(
                        new String[]{"defaults", "read", "-g", "AppleInterfaceStyle"}
                );
                String result = new String(p.getInputStream().readAllBytes()).strip();
                return result.equalsIgnoreCase("dark");
            } else if (os.contains("windows")) {
                Process p = Runtime.getRuntime().exec(new String[]{
                        "reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme"
                });
                String result = new String(p.getInputStream().readAllBytes());
                return result.contains("0x0");
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private void showAboutDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("About");
        dialog.setResizable(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(24));
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("about-dialog");

        Label title = new Label("⚡ ESP Flasher");
        title.getStyleClass().add("about-title");

        Label version = new Label("v1.0.0");
        version.getStyleClass().add("about-version");

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #3a3a3c;");

        Label author = new Label("Built by Ajinkya Gokhale");
        author.getStyleClass().add("about-author");

        Label email = new Label("✉  hi@ajinkyagokhale.com");
        email.getStyleClass().add("about-link");
        email.setOnMouseClicked(e ->
                getHostServices().showDocument("mailto:hi@ajinkyagokhale.com"));

        Label github = new Label("⚡  github.com/ajinkyagokhale");
        github.getStyleClass().add("about-link");
        github.setOnMouseClicked(e ->
                getHostServices().showDocument("https://github.com/ajinkyagokhale"));

        Label license = new Label("MIT License — 2026");
        license.getStyleClass().add("about-license");

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());
        closeBtn.setPrefWidth(100);

        content.getChildren().addAll(
                title, version, sep,
                author, email, github,
                license, closeBtn
        );

        Scene scene = new Scene(content, 280, 280);
        dialog.setScene(scene);
        dialog.show();
    }


    @Override
    public void start(Stage primaryStage) throws Exception {
        // set app icon
        String os = System.getProperty("os.name").toLowerCase();
        String iconFile = os.contains("mac") ? "/icons/icon.icns" : "/icons/icon.ico";
        var iconUrl = getClass().getResource(iconFile);
        if (iconUrl != null) {
            primaryStage.getIcons().add(
                    new javafx.scene.image.Image(iconUrl.toExternalForm())
            );
        }


        esptoolRunner = new EsptoolRunner();
        portWatcher = new PortWatcher();
        prereqChecker = new PrereqChecker();

        var successUrl = getClass().getResource("/sounds/success.wav");
        var failUrl = getClass().getResource("/sounds/failure.wav");

        if (successUrl != null && failUrl != null) {
            successSound = new AudioClip(successUrl.toExternalForm());
            failSound = new AudioClip(failUrl.toExternalForm());
        } else {
            System.out.println("Sound files not found at /sounds/success.wav or /sounds/failure.wav");
        }


        primaryStage.setTitle("ESP Flasher");
        primaryStage.setWidth(750);
        primaryStage.setHeight(650);

        VBox root = new VBox(10);  // 10px spacing between children
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root);
        if (isDarkMode()) {
            scene.getStylesheets().add(
                    getClass().getResource("/styles.css").toExternalForm()
            );
        }
        primaryStage.setScene(scene);
        // File row
        binPathField = new TextField();
        binPathField.setPromptText("Select firmware .bin file...");
        binPathField.setEditable(false);
        HBox.setHgrow(binPathField, Priority.ALWAYS);

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> browseBin(primaryStage));

        HBox fileRow = new HBox(10, new Label("Firmware"), binPathField, browseButton);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        //root.getChildren().add(fileRow);

        // Chip row
        chipCombo = new ComboBox<>();
        chipCombo.getItems().addAll(
                "auto", "esp32c6", "esp32", "esp32s2",
                "esp32s3", "esp32c3", "esp32h2", "esp8266"
        );
        chipCombo.setValue("auto");

        HBox chipRow = new HBox(10, new Label("Chip"), chipCombo);
        chipRow.setAlignment(Pos.CENTER_LEFT);

        //root.getChildren().add(chipRow);
        // Port row
        portCombo = new ComboBox<>();
        portCombo.setEditable(true);
        portCombo.setPromptText("Select port...");
        refreshPorts();
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshPorts());

        HBox portRow = new HBox(10, new Label("Port"), portCombo, refreshButton);
        portRow.setAlignment(Pos.CENTER_LEFT);

        //root.getChildren().add(portRow);

        // Baud row
        baudCombo = new ComboBox<>();
        baudCombo.getItems().addAll(
                "460800", "921600", "230400", "115200"
        );
        baudCombo.setValue("460800");

        HBox baudRow = new HBox(10, new Label("Baud Rate"), baudCombo);
        baudRow.setAlignment(Pos.CENTER_LEFT);
        //root.getChildren().add(baudRow);

// Offset row
        flashOffsetField = new TextField("0x0");
        flashOffsetField.setPrefWidth(120);

        HBox offsetRow = new HBox(10, new Label("Flash Offset"), flashOffsetField);
        offsetRow.setAlignment(Pos.CENTER_LEFT);
        //root.getChildren().add(offsetRow);
        VBox configCard = new VBox(10);
        configCard.getStyleClass().add("config-card");
        configCard.getChildren().addAll(
                fileRow, chipRow, portRow, baudRow, offsetRow
        );
        root.getChildren().add(configCard);
        // Buttons row
        flashButton = new Button("Flash Once");
        flashButton.setOnAction(e -> startFlash(null));

        factoryButton = new Button("Factory Mode");
        factoryButton.setOnAction(e -> startFactoryMode());


        flashCountLabel = new Label("Flashed: 0");
        flashCountLabel.getStyleClass().add("flash-badge");

        stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopAll());
        stopButton.setDisable(true);

//        Button aboutButton = new Button("About");
//        aboutButton.setOnAction(e -> showAboutDialog());


        HBox buttonRow = new HBox(10, flashButton, factoryButton, flashCountLabel);

        root.getChildren().add(buttonRow);
// Progress bar
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        statusLabel = new Label("Ready.");

        root.getChildren().addAll(progressBar, statusLabel);
        checkPrerequisites();

// Log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        root.getChildren().add(logArea);

        //footer
        Label footer = new Label("Built with ❤ by Ajinkya Gokhale");
        footer.setMaxWidth(Double.MAX_VALUE);
        footer.setAlignment(Pos.CENTER);
        footer.getStyleClass().add("footer");

        root.getChildren().add(footer);
        footer.setOnMouseClicked(e -> showAboutDialog());
        footer.setStyle("-fx-cursor: hand;");
        primaryStage.show();


    } //start-end

}
