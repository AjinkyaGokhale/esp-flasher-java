package com.ajinkyagokhale.espflasher.ui;

import com.ajinkyagokhale.espflasher.listener.FlashListener;
import com.ajinkyagokhale.espflasher.listener.PortListener;
import com.ajinkyagokhale.espflasher.model.AppSettings;
import com.ajinkyagokhale.espflasher.model.FlashConfig;
import com.ajinkyagokhale.espflasher.model.FlashResult;
import com.ajinkyagokhale.espflasher.service.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javafx.scene.media.AudioClip;

import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private Label flashCountNumber;
    private boolean isFactoryMode = false;

//settings
    private SettingsManager settingsManager;
    private FlashLogger flashLogger;

    // tracks state across flash lifecycle
    private String currentFlashPort;
    private String currentFlashMac;

    @Override
    public void onProgress(int percent) {
        Platform.runLater(() -> {
            progressBar.setProgress(percent / 100.0);
        });
    }

    @Override
    public void onLog(String line) {
        if (line.contains("MAC:")) {
            String[] parts = line.split("MAC:");
            if (parts.length > 1) currentFlashMac = parts[1].trim();
        }
        Platform.runLater(() -> logArea.appendText(line + "\n"));
    }

    @Override
    public void onComplete(boolean success, String message) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        flashLogger.append(new FlashResult(success, message, currentFlashMac, currentFlashPort, timestamp));

        Platform.runLater(() -> {
            statusLabel.setText(message);
            flashButton.setDisable(false);
            factoryButton.setDisable(false);
            stopButton.setDisable(true);
            if (success) {
                flashCount++;
                flashCountNumber.setText(String.valueOf(flashCount));
                flashCountLabel.setStyle("-fx-background-color: rgba(102,187,106,0.25); -fx-border-color: #66bb6a;");
                new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(800),
                        ev -> flashCountLabel.setStyle(""))
                ).play();
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

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Python Not Found");
        dialog.setResizable(false);

        Label heading = new Label("Python 3 is required to run esptool.");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        String os = System.getProperty("os.name", "").toLowerCase();
        String steps;
        if (os.contains("win")) {
            steps = """
                    1. Go to https://python.org/downloads
                    2. Download the latest Python 3 installer for Windows.
                    3. Run the installer — check "Add Python to PATH" before clicking Install.
                    4. Restart ESP Flasher after installation.""";
        } else if (os.contains("mac")) {
            steps = """
                    Option A — Homebrew (recommended):
                      brew install python3
                    
                    Option B — Installer:
                    1. Go to https://python.org/downloads
                    2. Download and run the macOS installer.
                    3. Restart ESP Flasher after installation.""";
        } else {
            steps = """
                    Debian / Ubuntu:
                      sudo apt install python3 python3-pip
                    
                    Fedora / RHEL:
                      sudo dnf install python3
                    
                    Arch:
                      sudo pacman -S python
                    
                    Restart ESP Flasher after installation.""";
        }

        TextArea guide = new TextArea(steps);
        guide.setEditable(false);
        guide.setWrapText(true);
        guide.setPrefHeight(140);
        guide.setPrefWidth(400);
        guide.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        Button downloadBtn = new Button("Open python.org");
        downloadBtn.setOnAction(e -> getHostServices().showDocument("https://python.org/downloads"));

        Button closeBtn = new Button("Close");
        closeBtn.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(10, downloadBtn, closeBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, heading, guide, buttons);
        root.setPadding(new Insets(16));

        dialog.setScene(new Scene(root));
        dialog.showAndWait();

        statusLabel.setText("⚠ Python not found — install Python and restart.");
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    private void autoInstallEsptool() {
        showEsptoolInstallDialog();
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
        currentFlashPort = port;
        currentFlashMac = null;
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


//    private void showSettingsDialog(Stage owner) {
//        Stage dialog = new Stage();
//        dialog.initOwner(owner);
//        dialog.initModality(Modality.APPLICATION_MODAL);
//        dialog.setTitle("Settings");
//
//        // ── Detected paths (read-only) ──────────────────────────────────
//        Label detectedHeading = new Label("Detected paths");
//        detectedHeading.setStyle("-fx-font-weight: bold;");
//
//        Label detectedPython  = detectedPathLabel("Python",  prereqChecker.getPythonCmd());
//        Label detectedPip     = detectedPathLabel("pip",     prereqChecker.getPipCmd());
//        Label detectedEsptool = detectedPathLabel("esptool", prereqChecker.getEsptoolCmd());
//
//        VBox detectedBox = new VBox(4, detectedPython, detectedPip, detectedEsptool);
//        detectedBox.setStyle("-fx-background-color: rgba(255,255,255,0.04); "
//                + "-fx-border-color: rgba(255,255,255,0.08); "
//                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");
//
//        // ── Custom overrides ────────────────────────────────────────────
//        Label overrideHeading = new Label("Custom overrides  (leave blank to use auto-detect)");
//        overrideHeading.setStyle("-fx-font-weight: bold;");
//
//        TextField pythonField = new TextField(PrereqChecker.getCustomPythonPath());
//        pythonField.setPromptText("e.g. C:\\Python313\\python.exe");
//        pythonField.setPrefWidth(400);
//        Button pythonBrowse = new Button("Browse...");
//        pythonBrowse.setOnAction(e -> {
//            FileChooser fc = new FileChooser();
//            fc.setTitle("Select Python executable");
//            File f = fc.showOpenDialog(dialog);
//            if (f != null) pythonField.setText(f.getAbsolutePath());
//        });
//
//        TextField esptoolField = new TextField(PrereqChecker.getCustomEsptoolPath());
//        esptoolField.setPromptText("e.g. C:\\Tools\\esptool.exe");
//        esptoolField.setPrefWidth(400);
//        Button esptoolBrowse = new Button("Browse...");
//        esptoolBrowse.setOnAction(e -> {
//            FileChooser fc = new FileChooser();
//            fc.setTitle("Select esptool executable");
//            File f = fc.showOpenDialog(dialog);
//            if (f != null) esptoolField.setText(f.getAbsolutePath());
//        });
//
//        Label status = new Label();
//
//        Button save = new Button("Save & Recheck");
//        save.setOnAction(e -> {
//            PrereqChecker.setCustomPaths(pythonField.getText(), esptoolField.getText());
//            status.setText("Rechecking...");
//            new Thread(() -> {
//                prereqChecker.checkAll();
//                Platform.runLater(() -> {
//                    detectedPython.setText(detectedText("Python", prereqChecker.getPythonCmd()));
//                    detectedPip.setText(detectedText("pip", prereqChecker.getPipCmd()));
//                    detectedEsptool.setText(detectedText("esptool", prereqChecker.getEsptoolCmd()));
//                    if (prereqChecker.isReady()) {
//                        status.setText("✓ Ready.");
//                        status.setStyle("-fx-text-fill: #66bb6a;");
//                        statusLabel.setText("Ready.");
//                        statusLabel.setStyle("");
//                    } else {
//                        status.setText("✗ esptool not found. Check paths.");
//                        status.setStyle("-fx-text-fill: red;");
//                    }
//                });
//            }).start();
//        });
//        Button close = new Button("Close");
//        close.setOnAction(e -> dialog.close());
//
//        HBox actions = new HBox(10, save, close);
//        actions.setAlignment(Pos.CENTER_RIGHT);
//
//        VBox content = new VBox(12,
//                detectedHeading,
//                detectedBox,
//                new Separator(),
//                overrideHeading,
//                new Label("Python:"),
//                new HBox(8, pythonField, pythonBrowse),
//                new Label("esptool:"),
//                new HBox(8, esptoolField, esptoolBrowse),
//                status,
//                actions
//        );
//        content.setPadding(new Insets(16));
//
//        dialog.setScene(new Scene(content));
//        dialog.show();
//    }
private void showSettingsDialog(Stage owner) {
    Stage dialog = new Stage();
    dialog.initOwner(owner);
    dialog.initModality(Modality.APPLICATION_MODAL);
    dialog.setTitle("Settings");
    dialog.setResizable(false);

    // ── Left menu ──────────────────────────────────────
    VBox leftMenu = new VBox(0);
    leftMenu.getStyleClass().add("settings-menu");
    leftMenu.setPrefWidth(150);

    Label menuTitle = new Label("Settings");
    menuTitle.getStyleClass().add("settings-menu-title");
    menuTitle.setPadding(new Insets(16));

    Button generalBtn = new Button("General");
    generalBtn.getStyleClass().add("settings-menu-item");
    generalBtn.setMaxWidth(Double.MAX_VALUE);

    Button pathsBtn = new Button("Paths");
    pathsBtn.getStyleClass().add("settings-menu-item");
    pathsBtn.setMaxWidth(Double.MAX_VALUE);

    Button logFileBtn = new Button("Log File");
    logFileBtn.getStyleClass().add("settings-menu-item");
    logFileBtn.setMaxWidth(Double.MAX_VALUE);

    leftMenu.getChildren().addAll(menuTitle, generalBtn, pathsBtn, logFileBtn);

    // ── Right panels ────────────────────────────────────
    VBox generalPane  = buildGeneralPane();
    VBox pathsPane    = buildPathsPane(dialog);
    VBox logFilePane  = buildLogFilePane(dialog);

    pathsPane.setVisible(false);
    logFilePane.setVisible(false);

    StackPane rightContent = new StackPane(generalPane, pathsPane, logFilePane);
    rightContent.setPadding(new Insets(20));
    rightContent.setPrefWidth(370);

    // ── Menu click handlers ─────────────────────────────
    generalBtn.setOnAction(e -> {
        generalPane.setVisible(true);
        pathsPane.setVisible(false);
        logFilePane.setVisible(false);
        setActiveMenu(generalBtn, pathsBtn, logFileBtn);
    });
    pathsBtn.setOnAction(e -> {
        generalPane.setVisible(false);
        pathsPane.setVisible(true);
        logFilePane.setVisible(false);
        setActiveMenu(pathsBtn, generalBtn, logFileBtn);
    });
    logFileBtn.setOnAction(e -> {
        generalPane.setVisible(false);
        pathsPane.setVisible(false);
        logFilePane.setVisible(true);
        setActiveMenu(logFileBtn, generalBtn, pathsBtn);
    });

    // General active by default
    generalBtn.getStyleClass().add("settings-menu-item-active");

    // ── Bottom bar ──────────────────────────────────────
    Button saveBtn = new Button("Save");
    saveBtn.getStyleClass().add("btn-primary");
    saveBtn.setOnAction(e -> {
        settingsManager.save();
        dialog.close();
    });

    Button cancelBtn = new Button("Cancel");
    cancelBtn.getStyleClass().add("btn-secondary");
    cancelBtn.setOnAction(e -> dialog.close());

    HBox bottomBar = new HBox(10, cancelBtn, saveBtn);
    bottomBar.setAlignment(Pos.CENTER_RIGHT);
    bottomBar.setPadding(new Insets(10, 20, 16, 20));

    // ── Main layout ─────────────────────────────────────
    HBox mainLayout = new HBox(leftMenu, rightContent);
    VBox root = new VBox(mainLayout, bottomBar);
    root.getStyleClass().add("settings-root");

    Scene scene = new Scene(root, 520, 380);
    scene.getStylesheets().add(
            Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm()
    );
    dialog.setScene(scene);
    dialog.show();
}

    // helper — sets active style on clicked button
    private void setActiveMenu(Button active, Button... others) {
        active.getStyleClass().add("settings-menu-item-active");
        for (Button b : others) b.getStyleClass().remove("settings-menu-item-active");
    }

    // ── General pane ────────────────────────────────────────
    private VBox buildGeneralPane() {
        VBox pane = new VBox(12);

        Label heading = new Label("General");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label info = new Label("Last used settings are saved automatically.");
        info.setStyle("-fx-text-fill: #888888;");

        pane.getChildren().addAll(heading, info);
        return pane;
    }

    // ── Paths pane — moved from old showSettingsDialog ───────
    private VBox buildPathsPane(Stage dialog) {
        VBox pane = new VBox(12);

        Label heading = new Label("Detected Paths");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label detectedPython  = detectedPathLabel("Python",  prereqChecker.getPythonCmd());
        Label detectedPip     = detectedPathLabel("pip",     prereqChecker.getPipCmd());
        Label detectedEsptool = detectedPathLabel("esptool", prereqChecker.getEsptoolCmd());

        VBox detectedBox = new VBox(4, detectedPython, detectedPip, detectedEsptool);
        detectedBox.setStyle("-fx-background-color: rgba(255,255,255,0.04); "
                + "-fx-border-color: rgba(255,255,255,0.08); "
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");

        Label overrideHeading = new Label("Custom Overrides");
        overrideHeading.setStyle("-fx-font-weight: bold;");

        TextField pythonField = new TextField(PrereqChecker.getCustomPythonPath());
        pythonField.setPromptText("e.g. /usr/bin/python3");
        HBox.setHgrow(pythonField, Priority.ALWAYS);
        Button pythonBrowse = new Button("Browse...");
        pythonBrowse.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(dialog);
            if (f != null) pythonField.setText(f.getAbsolutePath());
        });

        TextField esptoolField = new TextField(PrereqChecker.getCustomEsptoolPath());
        esptoolField.setPromptText("e.g. /usr/local/bin/esptool.py");
        HBox.setHgrow(esptoolField, Priority.ALWAYS);
        Button esptoolBrowse = new Button("Browse...");
        esptoolBrowse.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(dialog);
            if (f != null) esptoolField.setText(f.getAbsolutePath());
        });

        Label status = new Label();
        Button recheckBtn = new Button("Save & Recheck");
        recheckBtn.setOnAction(e -> {
            PrereqChecker.setCustomPaths(pythonField.getText(), esptoolField.getText());
            status.setText("Rechecking...");
            new Thread(() -> {
                prereqChecker.checkAll();
                Platform.runLater(() -> {
                    detectedPython.setText(detectedText("Python", prereqChecker.getPythonCmd()));
                    detectedPip.setText(detectedText("pip", prereqChecker.getPipCmd()));
                    detectedEsptool.setText(detectedText("esptool", prereqChecker.getEsptoolCmd()));
                    status.setText(prereqChecker.isReady() ? "✓ Ready." : "✗ esptool not found.");
                    status.setStyle(prereqChecker.isReady() ? "-fx-text-fill: green;" : "-fx-text-fill: red;");
                });
            }).start();
        });

        pane.getChildren().addAll(
                heading, detectedBox,
                new Separator(), overrideHeading,
                new Label("Python:"), new HBox(8, pythonField, pythonBrowse),
                new Label("esptool:"), new HBox(8, esptoolField, esptoolBrowse),
                status, recheckBtn
        );
        return pane;
    }

    // ── Log File pane ────────────────────────────────────────
    private VBox buildLogFilePane(Stage dialog) {
        VBox pane = new VBox(12);
        AppSettings settings = settingsManager.getSettings();

        Label heading = new Label("Log File");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label infoBtn = new Label("ℹ");
        infoBtn.setStyle(
                "-fx-border-color: #666; -fx-border-radius: 50%; -fx-background-radius: 50%; " +
                "-fx-background-color: #2a2a2a; -fx-font-size: 12px; -fx-text-fill: #cccccc; " +
                "-fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; " +
                "-fx-alignment: center; -fx-cursor: hand;");
        infoBtn.setOnMouseClicked(e -> {
            Alert info = new Alert(Alert.AlertType.NONE);
            info.setTitle("Why use a log file?");
            info.getButtonTypes().add(javafx.scene.control.ButtonType.OK);

            Label title = new Label("MAC Address Provisioning Audit");
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

            Label recordedLabel = new Label("Each flash attempt is recorded with:");
            recordedLabel.setStyle("-fx-font-size: 12px;");

            VBox bullets = new VBox(4,
                    bullet("Timestamp"),
                    bullet("Serial port"),
                    bullet("Device MAC address"),
                    bullet("Status  (success / failed)")
            );
            bullets.setStyle("-fx-padding: 0 0 0 12;");

            Label useCaseLabel = new Label("Use case — backend provisioning check:");
            useCaseLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 8 0 0 0;");

            Label useCaseBody = new Label(
                    "Upload flash-log.csv to your backend and cross-reference the MAC " +
                    "addresses against your provisioned-device registry. This lets you verify " +
                    "that every flashed device was registered, catch devices that were flashed " +
                    "but never provisioned, and flag duplicates (same MAC flashed twice)."
            );
            useCaseBody.setStyle("-fx-font-size: 12px;");
            useCaseBody.setWrapText(true);
            useCaseBody.setMaxWidth(380);

            VBox content = new VBox(8, title, recordedLabel, bullets, useCaseLabel, useCaseBody);
            content.setStyle("-fx-padding: 4;");

            info.getDialogPane().setContent(content);
            info.getDialogPane().getStylesheets().addAll(dialog.getScene().getStylesheets());
            info.showAndWait();
        });

        HBox headingRow = new HBox(8, heading, infoBtn);
        headingRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        CheckBox enableToggle = new CheckBox("Enable log file");
        enableToggle.setSelected(settings.isLogFileEnabled());
        enableToggle.setStyle(settings.isLogFileEnabled() ? "-fx-text-fill: #66bb6a;" : "");


        Label pathLabel = new Label("Log folder:");
        TextField pathField = new TextField(settings.getLogFilePath());
        pathField.setDisable(!settings.isLogFileEnabled());
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browseBtn = new Button("Browse...");
        browseBtn.setDisable(!settings.isLogFileEnabled());
        browseBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
            dc.setTitle("Select Log Folder");
            File dir = dc.showDialog(dialog);
            if (dir != null) pathField.setText(dir.getAbsolutePath());
        });

        Label formatInfo = new Label("Format: timestamp, port, MAC address, status");
        formatInfo.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");

        // enable/disable fields when toggle changes
        enableToggle.setOnAction(e -> {
            boolean enabled = enableToggle.isSelected();
            pathField.setDisable(!enabled);
            browseBtn.setDisable(!enabled);
            settings.setLogFileEnabled(enabled);
            enableToggle.setStyle(enabled ? "-fx-text-fill: #66bb6a;" : "");
        });

        // save path on change
        pathField.textProperty().addListener((obs, old, val) ->
                settings.setLogFilePath(val)
        );

        pane.getChildren().addAll(
                headingRow,
                enableToggle,
                pathLabel,
                new HBox(8, pathField, browseBtn),
                formatInfo
        );
        return pane;
    }
    private Label bullet(String text) {
        Label l = new Label("• " + text);
        l.setStyle("-fx-font-size: 12px;");
        return l;
    }

    private Label detectedPathLabel(String name, String value) {
        Label l = new Label(detectedText(name, value));
        l.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        return l;
    }

    private String detectedText(String name, String value) {
        if (value == null || value.isBlank())
            return "✗  " + name + ": not found";
        return "✓  " + name + ": " + value;
    }

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
        statusLabel.setStyle("");
        statusLabel.setOnMouseClicked(null);
        if (prereqChecker.getPythonCmd() == null) {
            showPythonMissingDialog();
            return;
        }
        if (prereqChecker.getPipCmd() == null) {
            statusLabel.setText("⚠ pip not found — reinstall Python with pip included.");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        showEsptoolInstallDialog();
    }

    private void showEsptoolInstallDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Installing esptool");
        dialog.setResizable(false);

        Label titleLabel = new Label("Installing esptool via pip...");
        titleLabel.setStyle("-fx-font-weight: bold;");

        ProgressBar bar = new ProgressBar();
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setMaxWidth(Double.MAX_VALUE);

        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.setPrefHeight(180);
        output.setPrefWidth(440);

        Label statusMsg = new Label("Please wait...");

        Button closeBtn = new Button("Close");
        closeBtn.setDisable(true);
        closeBtn.setOnAction(e -> dialog.close());

        VBox root = new VBox(10, titleLabel, bar, output, statusMsg, closeBtn);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.CENTER_LEFT);

        dialog.setScene(new Scene(root));

        Thread thread = new Thread(() -> {
            boolean success = prereqChecker.installEsptool(line ->
                    Platform.runLater(() -> output.appendText(line + "\n"))
            );

            Platform.runLater(() -> {
                bar.setProgress(1.0);
                if (success) {
                    logArea.clear();
                    statusLabel.setText("Ready.");
                    statusLabel.setStyle("");
                    flashButton.setDisable(false);
                    factoryButton.setDisable(false);
                    dialog.close();
                } else {
                    statusMsg.setText("✗ Install failed. Check output above.");
                    statusMsg.setStyle("-fx-text-fill: red;");
                    statusLabel.setText("⚠ esptool install failed.");
                    closeBtn.setDisable(false);
                }
            });
        });
        thread.setDaemon(true);
        thread.start();

        dialog.show();
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
                    statusLabel.setStyle("-fx-text-fill: #2196f3; -fx-underline: true; -fx-cursor: hand;");
                    statusLabel.setOnMouseClicked(e -> installEsptool());
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
                String result = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
                return result.equalsIgnoreCase("dark");
            } else if (os.contains("windows")) {
                Process p = Runtime.getRuntime().exec(new String[]{
                        "reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme"
                });
                String result = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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

        var iconUrl = getClass().getResource("/icons/icon.png");
        if (iconUrl != null) {
            javafx.scene.image.ImageView logo = new javafx.scene.image.ImageView(
                    new javafx.scene.image.Image(iconUrl.toExternalForm()));
            logo.setFitWidth(72);
            logo.setFitHeight(72);
            logo.setPreserveRatio(true);
            content.getChildren().add(logo);
        }

        Label title = new Label("ESP Flasher");
        title.getStyleClass().add("about-title");

        String appVersion = new UpdateService().currentVersion();
        Label version = new Label("v" + appVersion);
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

        Scene scene = new Scene(content, 280, 360);
        dialog.setScene(scene);
        dialog.show();
    }

    @Override
    public void stop() {
        AppSettings settings = settingsManager.getSettings();
        settings.setLastChip(chipCombo.getValue());
        settings.setLastBaudRate(baudCombo.getValue());
        settings.setLastBinPath(binPathField.getText());
        settings.setLastPort(portCombo.getValue());
        settingsManager.save();

        portWatcher.stopWatching();
        esptoolRunner.stopFlashing();
    }


    @Override
    public void start(Stage primaryStage) throws Exception {
        checkForUpdates();
        // set app icon — JavaFX Image only supports PNG/JPEG/BMP, not .ico/.icns
        var iconUrl = getClass().getResource("/icons/icon.png");
        if (iconUrl != null) {
            primaryStage.getIcons().add(new javafx.scene.image.Image(iconUrl.toExternalForm()));
        }


        esptoolRunner = new EsptoolRunner();
        portWatcher = new PortWatcher();
        prereqChecker = new PrereqChecker();
        settingsManager = new SettingsManager();
        settingsManager.load();
        flashLogger = new FlashLogger(settingsManager.getSettings());

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


        flashCountNumber = new Label("0");
        flashCountNumber.getStyleClass().add("flash-count-number");

        Label flashCountSub = new Label("flashed");
        flashCountSub.getStyleClass().add("flash-count-sub");

        flashCountLabel = new Label();
        flashCountLabel.getStyleClass().add("flash-count-card");
        VBox flashCard = new VBox(0, flashCountNumber, flashCountSub);
        flashCard.setAlignment(Pos.CENTER);
        flashCountLabel.setGraphic(flashCard);

        stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopAll());
        stopButton.setDisable(true);

//        Button aboutButton = new Button("About");
//        aboutButton.setOnAction(e -> showAboutDialog());


        Button settingsButton = new Button("Settings");
        settingsButton.setOnAction(e -> showSettingsDialog(primaryStage));

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonRow = new HBox(10, flashButton, factoryButton, settingsButton, spacer, flashCountLabel);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().add(buttonRow);
// Progress bar
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        statusLabel = new Label("Ready.");

        root.getChildren().addAll(progressBar, statusLabel);
        checkPrerequisites();


        //apply last settings
        AppSettings settings = settingsManager.getSettings();
        if (!settings.getLastChip().isEmpty()) chipCombo.setValue(settings.getLastChip());
        if (!settings.getLastBaudRate().isEmpty()) baudCombo.setValue(settings.getLastBaudRate());
        if (!settings.getLastBinPath().isEmpty()) binPathField.setText(settings.getLastBinPath());


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
        if (isDarkMode()) applyDarkTitleBar(primaryStage);

    } //start-end

    private interface Dwmapi extends com.sun.jna.Library {
        Dwmapi INSTANCE = com.sun.jna.Native.load("dwmapi", Dwmapi.class);
        void DwmSetWindowAttribute(com.sun.jna.platform.win32.WinDef.HWND hwnd, int attr,
                                   com.sun.jna.ptr.IntByReference value, int size);
    }

    @SuppressFBWarnings("DE_MIGHT_IGNORE")
    private void applyDarkTitleBar(Stage stage) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            com.sun.jna.platform.win32.WinDef.HWND hwnd =
                    com.sun.jna.platform.win32.User32.INSTANCE.FindWindow(null, stage.getTitle());
            if (hwnd == null) return;
            com.sun.jna.ptr.IntByReference dark = new com.sun.jna.ptr.IntByReference(1);
            Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, 20, dark, 4); // Windows 10 20H1+
            Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, 19, dark, 4); // older Win10 builds
        } catch (Exception ignored) {}
    }

    private void checkForUpdates() {
        Thread worker = new Thread(() -> {
            UpdateService updates = new UpdateService();
            String current = updates.currentVersion();
            updates.latestRelease()
                    .filter(release -> updates.isNewer(release.version(), current))
                    .ifPresent(release ->
                            Platform.runLater(() -> promptForcedUpdate(updates, release, current)));
        }, "update-check");
        worker.setDaemon(true);
        worker.start();
    }

    @SuppressFBWarnings("DM_EXIT")
    private void promptForcedUpdate(UpdateService updates, UpdateService.Release release, String current) {
        ButtonType updateNow = new ButtonType("Update Now", ButtonBar.ButtonData.OK_DONE);
        ButtonType quit = new ButtonType("Quit", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Update Required");
        alert.setHeaderText("A new version of ESP Flasher is available");
        alert.setContentText("Installed: " + current + "\nLatest: " + release.version()
                + "\n\nYou must update to continue.");
        alert.getButtonTypes().setAll(updateNow, quit);
        alert.initModality(Modality.APPLICATION_MODAL);

        Optional<ButtonType> choice = alert.showAndWait();
        while (choice.isEmpty()) {
            choice = alert.showAndWait();
        }

        if (choice.get() == updateNow) {
            downloadUpdate(updates, release);
        } else {
            System.exit(0);
        }
    }

    private void downloadUpdate(UpdateService updates, UpdateService.Release release) {
        AtomicBoolean cancelled = new AtomicBoolean(false);

        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(380);
        Label info = new Label("Starting download...");
        Label hint = new Label("The app will restart to install the update.");
        hint.getStyleClass().add("footer");
        VBox box = new VBox(10, info, bar, hint);
        box.setPadding(new Insets(16));

        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert dialog = new Alert(Alert.AlertType.NONE);
        dialog.setTitle("Downloading Update");
        dialog.setHeaderText("Updating to " + release.version());
        dialog.getDialogPane().setContent(box);
        dialog.getButtonTypes().setAll(cancelType);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setOnHidden(e -> cancelled.set(true));

        Thread worker = new Thread(() -> {
            try {
                updates.downloadAndLaunch(release,
                        (downloaded, total) -> Platform.runLater(
                                () -> updateProgress(bar, info, downloaded, total)),
                        cancelled);
                Platform.runLater(() -> {
                    dialog.close();
                    promptForcedUpdate(updates, release, updates.currentVersion());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    dialog.close();
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Update Failed");
                    error.setHeaderText("Could not download the update");
                    error.setContentText(e.getMessage());
                    error.showAndWait();
                    promptForcedUpdate(updates, release, updates.currentVersion());
                });
            }
        }, "update-download");
        worker.setDaemon(true);
        worker.start();

        dialog.show();
    }

    private void updateProgress(ProgressBar bar, Label info, long downloaded, long total) {
        double mb = downloaded / 1048576.0;
        if (total > 0) {
            double fraction = (double) downloaded / total;
            bar.setProgress(fraction);
            info.setText(String.format("%.1f MB / %.1f MB  (%d%%)",
                    mb, total / 1048576.0, (int) (fraction * 100)));
        } else {
            bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            info.setText(String.format("%.1f MB downloaded", mb));
        }
    }

}
