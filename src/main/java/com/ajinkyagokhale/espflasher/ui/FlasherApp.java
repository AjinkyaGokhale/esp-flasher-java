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
import java.awt.*;
import java.io.File;
import java.util.Objects;

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
        });
    }

    @Override
    public void onNewPort(String portName) {

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

    private void startFlash() {
        //guard
        if (!prereqChecker.isReady()) {
            statusLabel.setText("esptool not found. Please install it first.");
            return;
        }

        // 1. validate inputs
        String binPath = binPathField.getText();
        if (binPath.isEmpty()) {
            statusLabel.setText("Please select a firmware file.");
            return;
        }

        String port = portCombo.getValue();
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
    }

    private void stopAll() {
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
        for (SerialPort port : SerialPort.getCommPorts()) {
            portCombo.getItems().add(port.getSystemPortPath());
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


    @Override
    public void start(Stage primaryStage) throws Exception {


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
        flashButton.setOnAction(e -> startFlash());

        factoryButton = new Button("Factory Mode");
        factoryButton.setOnAction(e -> startFactoryMode());



        flashCountLabel = new Label("Flashed: 0");
        flashCountLabel.getStyleClass().add("flash-badge");

        stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopAll());
        stopButton.setDisable(true);

        HBox buttonRow = new HBox(10, flashButton, factoryButton, stopButton, flashCountLabel);

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
        primaryStage.show();


    } //start-end

}
