package com.ajinkyagokhale.espflasher.ui;

import com.ajinkyagokhale.espflasher.listener.FlashListener;
import com.ajinkyagokhale.espflasher.listener.PortListener;
import com.ajinkyagokhale.espflasher.model.AppSettings;
import com.ajinkyagokhale.espflasher.model.FirmwareDefinition;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlasherApp extends Application implements FlashListener, PortListener {

    // ── UI controls ─────────────────────────────────────────
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
    private Label flashCountLabel;
    private Label flashCountNumber;

    // ── Services ────────────────────────────────────────────
    private EsptoolRunner esptoolRunner;
    private PortWatcher portWatcher;
    private PrereqChecker prereqChecker;
    private SettingsManager settingsManager;
    private FlashLogger flashLogger;

    // ── Audio ───────────────────────────────────────────────
    private AudioClip successSound;
    private AudioClip failSound;

    // ── Flash state ─────────────────────────────────────────
    private int flashCount = 0;
    private boolean isFactoryMode = false;
    private String currentFlashPort;
    private String currentFlashMac;

    private RadioButton myBinaryRadio;
    private RadioButton popularFirmwareRadio;
    private ComboBox<FirmwareDefinition> firmwareCombo;
    private Label firmwareDescLabel;
    private Label firmwareVersionLabel;
    private VBox popularFirmwareRow;
    private FirmwareDownloader firmwareDownloader;
    private FirmwareDefinition selectedFirmware;

    // Top-level views (toggled by toolbar / Explore button)
    private VBox flasherView;
    private VBox settingsView;
    private VBox exploreView;
    private Button toolbarFlasherBtn;
    private Button toolbarSettingsBtn;


    // ════════════════════════════════════════════════════════
    // Application lifecycle
    // ════════════════════════════════════════════════════════

    @Override
    public void start(Stage primaryStage) throws Exception {
        checkForUpdates();


        initIcon(primaryStage);
        initService();
        initSound();

        primaryStage.setTitle("ESP Flasher");
        primaryStage.setWidth(750);
        primaryStage.setHeight(650);
        primaryStage.setMinWidth(750);
        primaryStage.setMinHeight(650);

        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        // Flasher view container — all existing build methods feed into this
        flasherView = new VBox(10);
        VBox.setVgrow(flasherView, Priority.ALWAYS);

        buildConfigCard(primaryStage, flasherView);
        buildButtonRow(primaryStage, flasherView);
        addProgressBar(flasherView);
        checkPrerequisites();
        applyLastSettings();
        buildLogArea(flasherView);

        // Settings view — initially hidden
        settingsView = buildSettingsView(primaryStage);
        VBox.setVgrow(settingsView, Priority.ALWAYS);
        settingsView.setVisible(false);
        settingsView.setManaged(false);

        // Explore view — initially hidden
        exploreView = buildExploreView();
        VBox.setVgrow(exploreView, Priority.ALWAYS);
        exploreView.setVisible(false);
        exploreView.setManaged(false);

        HBox toolbar = buildToolbar();
        root.getChildren().addAll(toolbar, flasherView, settingsView, exploreView);

        // Footer
        buildFooter(root);

        Scene scene = new Scene(root);
        if (isDarkMode()) {
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/styles.css")).toExternalForm());
        }
        primaryStage.setScene(scene);

        primaryStage.show();
        if (isDarkMode()) applyDarkTitleBar(primaryStage);
    }

    private HBox buildToolbar() {
        toolbarFlasherBtn = new Button("Flasher");
        toolbarSettingsBtn = new Button("Settings");

        toolbarFlasherBtn.getStyleClass().addAll("toolbar-btn", "toolbar-btn-active");
        toolbarSettingsBtn.getStyleClass().add("toolbar-btn");

        toolbarFlasherBtn.setOnAction(e -> showView(flasherView));
        toolbarSettingsBtn.setOnAction(e -> showView(settingsView));

        HBox toolbar = new HBox(2, toolbarFlasherBtn, toolbarSettingsBtn);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER);

        HBox toolbarWrapper = new HBox(toolbar);
        toolbarWrapper.setAlignment(Pos.CENTER);
        toolbarWrapper.setPadding(new Insets(4, 0, 4, 0));
        return toolbarWrapper;
    }

    /** Show one of flasher/settings/explore; hide the others. Updates toolbar active state. */
    private void showView(VBox target) {
        VBox[] all = { flasherView, settingsView, exploreView };
        for (VBox v : all) {
            boolean show = v == target;
            v.setVisible(show);
            v.setManaged(show);
        }
        toolbarFlasherBtn.getStyleClass().remove("toolbar-btn-active");
        toolbarSettingsBtn.getStyleClass().remove("toolbar-btn-active");
        if (target == flasherView) {
            toolbarFlasherBtn.getStyleClass().add("toolbar-btn-active");
        } else if (target == settingsView) {
            toolbarSettingsBtn.getStyleClass().add("toolbar-btn-active");
        }
        // explore view leaves both toolbar buttons inactive (deliberate)
    }

    private void initIcon(Stage primaryStage) {
        // set app icon — JavaFX Image only supports PNG/JPEG/BMP, not .ico/.icns
        var iconUrl = getClass().getResource("/icons/icon.png");
        if (iconUrl != null) {
            primaryStage.getIcons().add(new javafx.scene.image.Image(iconUrl.toExternalForm()));
        }
    }

    private void buildFooter(VBox root) {
        Label footer = new Label("Built with ❤ by Ajinkya Gokhale");
        footer.setMaxWidth(Double.MAX_VALUE);
        footer.setAlignment(Pos.CENTER);
        footer.getStyleClass().add("footer");
        footer.setOnMouseClicked(e -> showAboutDialog());
        footer.setStyle("-fx-cursor: hand;");
        root.getChildren().add(footer);
    }

    private void applyLastSettings() {
        // Apply last-used settings
        AppSettings settings = settingsManager.getSettings();
        if (!settings.getLastChip().isEmpty()) chipCombo.setValue(settings.getLastChip());
        if (!settings.getLastBaudRate().isEmpty()) baudCombo.setValue(settings.getLastBaudRate());
        if (!settings.getLastBinPath().isEmpty()) binPathField.setText(settings.getLastBinPath());
    }

    private void initSound() {
        var successUrl = getClass().getResource("/sounds/success.wav");
        var failUrl = getClass().getResource("/sounds/failure.wav");
        if (successUrl != null && failUrl != null) {
            successSound = new AudioClip(successUrl.toExternalForm());
            failSound = new AudioClip(failUrl.toExternalForm());
        } else {
            System.out.println("Sound files not found at /sounds/success.wav or /sounds/failure.wav");
        }
    }

    private void initService() {
        esptoolRunner = new EsptoolRunner();
        portWatcher = new PortWatcher();
        prereqChecker = new PrereqChecker();
        settingsManager = new SettingsManager();
        settingsManager.load();
        flashLogger = new FlashLogger(settingsManager.getSettings());
        firmwareDownloader = new FirmwareDownloader();
    }

    private void buildLogArea(VBox root) {
        // Log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        root.getChildren().add(logArea);

        // Route firmware-downloader diagnostics here (thread-safe via Platform.runLater)
        firmwareDownloader.setLogger(msg ->
                Platform.runLater(() -> logArea.appendText(msg + "\n"))
        );
    }

    private void addProgressBar(VBox root) {
        // Progress + status
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        statusLabel = new Label("Ready.");
        root.getChildren().addAll(progressBar, statusLabel);
    }

    private void buildButtonRow(Stage primaryStage, VBox root) {

        // Action buttons + flash counter
        flashButton = new Button("Flash Once");
        flashButton.setOnAction(e -> startFlash(null));

        factoryButton = new Button("Factory Mode");
        factoryButton.setOnAction(e -> startFactoryMode());

        stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopAll());
        stopButton.setDisable(true);

        flashCountNumber = new Label("0");
        flashCountNumber.getStyleClass().add("flash-count-number");
        Label flashCountSub = new Label("flashed");
        flashCountSub.getStyleClass().add("flash-count-sub");
        flashCountLabel = new Label();
        flashCountLabel.getStyleClass().add("flash-count-card");
        VBox flashCard = new VBox(0, flashCountNumber, flashCountSub);
        flashCard.setAlignment(Pos.CENTER);
        flashCountLabel.setGraphic(flashCard);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonRow = new HBox(10, flashButton, factoryButton, spacer, flashCountLabel);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().add(buttonRow);
    }
    private void buildConfigCard(Stage primaryStage, VBox root) {

        // ── Firmware source selector ────────────────────────
        ComboBox<String> firmwareSourceCombo = new ComboBox<>();
        firmwareSourceCombo.getItems().add("Custom Binary");
        FirmwareCatalog.getCatalog().forEach(f ->
                firmwareSourceCombo.getItems().add(f.getName())
        );
        firmwareSourceCombo.setValue("Custom Binary");

        // ── Inline firmware description (next to Source dropdown) ──
        firmwareDescLabel = new Label("");
        firmwareDescLabel.getStyleClass().add("config-label");
        firmwareDescLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        firmwareDescLabel.setWrapText(true);
        HBox.setHgrow(firmwareDescLabel, Priority.ALWAYS);

        Button exploreBtn = new Button("Explore Popular Projects →");
        exploreBtn.getStyleClass().add("link-button");
        exploreBtn.setOnAction(e -> showView(exploreView));

        HBox sourceRow = new HBox(10, new Label("Source"), firmwareSourceCombo, firmwareDescLabel, exploreBtn);
        sourceRow.setAlignment(Pos.CENTER_LEFT);

        // ── Custom binary file row ──────────────────────────
        binPathField = new TextField();
        binPathField.setPromptText("Select firmware .bin file...");
        binPathField.setEditable(false);
        HBox.setHgrow(binPathField, Priority.ALWAYS);

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> browseBin(primaryStage));

        HBox fileRow = new HBox(10, new Label("Firmware"), binPathField, browseButton);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        // ── Popular firmware version row ────────────────────
        firmwareVersionLabel = new Label("");
        firmwareVersionLabel.setStyle("-fx-text-fill: #66bb6a; -fx-font-size: 11px;");

        popularFirmwareRow = new VBox(4, firmwareVersionLabel);
        popularFirmwareRow.setVisible(false);
        popularFirmwareRow.setManaged(false);

        // ── Source toggle behavior ──────────────────────────
        firmwareSourceCombo.setOnAction(e -> {
            String selected = firmwareSourceCombo.getValue();
            boolean isCustom = selected.equals("Custom Binary");

            fileRow.setVisible(isCustom);
            fileRow.setManaged(isCustom);
            popularFirmwareRow.setVisible(!isCustom);
            popularFirmwareRow.setManaged(!isCustom);

            if (isCustom) {
                firmwareDescLabel.setText("");
                selectedFirmware = null;
                updateChipListForFirmware(null);
                return;
            }

            // find matching FirmwareDefinition
            FirmwareDefinition def = FirmwareCatalog.getCatalog().stream()
                    .filter(f -> f.getName().equals(selected))
                    .findFirst()
                    .orElse(null);

            selectedFirmware = def;
            updateChipListForFirmware(def);

            if (def != null) {
                firmwareDescLabel.setText(def.getDescription());
                firmwareVersionLabel.setText("Fetching version...");
                new Thread(() -> {
                    boolean online = firmwareDownloader.isOnline();
                    if (!online) {
                        Platform.runLater(() ->
                                firmwareVersionLabel.setText("⚠ No internet connection")
                        );
                        return;
                    }
                    String version = firmwareDownloader.fetchLatestVersion(def);
                    Platform.runLater(() ->
                            firmwareVersionLabel.setText("Latest: v" + version)
                    );
                }).start();
            }
        });

        // ── Chip row ────────────────────────────────────────
        chipCombo = new ComboBox<>();
        chipCombo.getItems().addAll(
                "auto", "esp32c6", "esp32", "esp32s2",
                "esp32s3", "esp32c3", "esp32h2", "esp8266"
        );
        chipCombo.setValue("auto");
        HBox chipRow = new HBox(10, new Label("Chip"), chipCombo);
        chipRow.setAlignment(Pos.CENTER_LEFT);

        // ── Port row ────────────────────────────────────────
        portCombo = new ComboBox<>();
        portCombo.setEditable(true);
        portCombo.setPromptText("Select port...");
        refreshPorts();
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshPorts());
        HBox portRow = new HBox(10, new Label("Port"), portCombo, refreshButton);
        portRow.setAlignment(Pos.CENTER_LEFT);

        // ── Baud row ────────────────────────────────────────
        baudCombo = new ComboBox<>();
        baudCombo.getItems().addAll("460800", "921600", "230400", "115200");
        baudCombo.setValue("460800");
        HBox baudRow = new HBox(10, new Label("Baud Rate"), baudCombo);
        baudRow.setAlignment(Pos.CENTER_LEFT);

        // ── Offset row ──────────────────────────────────────
        flashOffsetField = new TextField("0x0");
        flashOffsetField.setPrefWidth(120);
        HBox offsetRow = new HBox(10, new Label("Flash Offset"), flashOffsetField);
        offsetRow.setAlignment(Pos.CENTER_LEFT);

        // ── Config card ─────────────────────────────────────
        VBox configCard = new VBox(10);
        configCard.getStyleClass().add("config-card");
        configCard.setMaxWidth(Double.MAX_VALUE);
        configCard.setFillWidth(true);
        configCard.getChildren().addAll(
                sourceRow,
                fileRow,
                popularFirmwareRow,
                chipRow, portRow, baudRow, offsetRow
        );
        root.getChildren().add(configCard);
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


    // ════════════════════════════════════════════════════════
    // Listener callbacks (FlashListener, PortListener)
    // ════════════════════════════════════════════════════════

    @Override
    public void onProgress(int percent) {
        Platform.runLater(() -> progressBar.setProgress(percent / 100.0));
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


    // ════════════════════════════════════════════════════════
    // Flash actions
    // ════════════════════════════════════════════════════════

    private void startFlash(String overridePort) {
        if (!prereqChecker.isReady()) {
            statusLabel.setText("esptool not found. Please install it first.");
            return;
        }

        String port = overridePort != null ? overridePort : portCombo.getValue();
        if (port == null || port.isEmpty()) {
            statusLabel.setText("Please select a port.");
            return;
        }

        if (selectedFirmware != null) {
            downloadAndFlash(selectedFirmware, port);
            return;
        }

        String binPath = binPathField.getText();
        if (binPath.isEmpty()) {
            statusLabel.setText("Please select a firmware file.");
            return;
        }

        runFlash(port, binPath);
    }

    private void runFlash(String port, String binPath) {
        FlashConfig config = new FlashConfig(
                chipCombo.getValue(),
                Integer.parseInt(baudCombo.getValue()),
                port,
                binPath,
                flashOffsetField.getText(),
                prereqChecker.getEsptoolCmd()
        );

        flashButton.setDisable(true);
        stopButton.setDisable(false);
        progressBar.setStyle("");
        progressBar.setProgress(0);
        statusLabel.setText("Flashing...");

        currentFlashPort = port;
        currentFlashMac = null;
        esptoolRunner.startFlashing(config, this);
    }

    private void downloadAndFlash(FirmwareDefinition fw, String port) {
        flashButton.setDisable(true);
        stopButton.setDisable(false);
        progressBar.setStyle("");
        progressBar.setProgress(0);
        statusLabel.setText("Downloading " + fw.getName() + "...");

        String chip = chipCombo.getValue();
        String binName = fw.getBinForChip(chip);
        if (binName == null) {
            Platform.runLater(() -> {
                statusLabel.setText("No binary defined for " + chip);
                statusLabel.setStyle("-fx-text-fill: red;");
                flashButton.setDisable(false);
                stopButton.setDisable(true);
            });
            return;
        }

        new Thread(() -> {
            try {
                String url = firmwareDownloader.fetchDownloadUrl(fw, chip);
                if (url == null) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Could not find download URL for " + binName);
                        statusLabel.setStyle("-fx-text-fill: red;");
                        flashButton.setDisable(false);
                        stopButton.setDisable(true);
                    });
                    return;
                }

                FirmwareDownloader.DownloadListener listener = (downloaded, total) ->
                        Platform.runLater(() -> {
                            if (total > 0) progressBar.setProgress((double) downloaded / total);
                        });

                String version = firmwareDownloader.fetchLatestVersion(fw);
                String localPath = binName.toLowerCase().endsWith(".zip")
                        ? firmwareDownloader.downloadAndExtract(url, binName, chip, version, listener)
                        : firmwareDownloader.download(url, binName, listener);

                Platform.runLater(() -> {
                    binPathField.setText(localPath);
                    progressBar.setProgress(0);
                    runFlash(port, localPath);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    logArea.appendText("[download] " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n");
                    statusLabel.setText("Download failed.");
                    statusLabel.setStyle("-fx-text-fill: red;");
                    flashButton.setDisable(false);
                    stopButton.setDisable(true);
                });
            }
        }).start();
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

        // Confirmation dialog
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Start Factory Mode");
        confirm.setHeaderText("Ready to flash?");

        Label binLabel = new Label(new File(binPathField.getText()).getName());
        binLabel.getStyleClass().add("confirm-bin-name");

        Label chipLabel = new Label("Chip: " + chipCombo.getValue() + "   Baud: " + baudCombo.getValue());
        chipLabel.getStyleClass().add("confirm-details");

        VBox content = new VBox(8, binLabel, chipLabel);
        confirm.getDialogPane().setContent(content);
        confirm.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm()
        );
        confirm.setGraphic(null);

        ButtonType startBtn = new ButtonType("Start Factory", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(startBtn, cancelBtn);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != startBtn) return;

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


    // ════════════════════════════════════════════════════════
    // Settings dialog
    // ════════════════════════════════════════════════════════

    private VBox buildSettingsView(Stage parentStage) {
        // Left menu
        VBox leftMenu = new VBox(0);
        leftMenu.getStyleClass().add("settings-menu");
        leftMenu.setPrefWidth(150);
        leftMenu.setMinWidth(150);

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

        // Right panels
        VBox generalPane = buildGeneralPane();
        VBox pathsPane = buildPathsPane(parentStage);
        VBox logFilePane = buildLogFilePane(parentStage);

        pathsPane.setVisible(false);
        logFilePane.setVisible(false);

        StackPane rightContent = new StackPane(generalPane, pathsPane, logFilePane);
        rightContent.setPadding(new Insets(20));
        HBox.setHgrow(rightContent, Priority.ALWAYS);

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

        generalBtn.getStyleClass().add("settings-menu-item-active");

        HBox mainLayout = new HBox(leftMenu, rightContent);
        VBox.setVgrow(mainLayout, Priority.ALWAYS);

        VBox view = new VBox(mainLayout);
        view.getStyleClass().add("settings-root");
        return view;
    }

    private void setActiveMenu(Button active, Button... others) {
        active.getStyleClass().add("settings-menu-item-active");
        for (Button b : others) b.getStyleClass().remove("settings-menu-item-active");
    }

    private VBox buildGeneralPane() {
        VBox pane = new VBox(12);

        Label heading = new Label("General");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label info = new Label("Last used settings are saved automatically.");
        info.setStyle("-fx-text-fill: #888888;");

        pane.getChildren().addAll(heading, info);
        return pane;
    }

    private VBox buildPathsPane(Stage dialog) {
        VBox pane = new VBox(12);

        Label heading = new Label("Detected Paths");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label detectedPython = detectedPathLabel("Python", prereqChecker.getPythonCmd());
        Label detectedPip = detectedPathLabel("pip", prereqChecker.getPipCmd());
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
            info.getButtonTypes().add(ButtonType.OK);

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
        headingRow.setAlignment(Pos.CENTER_LEFT);

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
            if (dir != null) {
                pathField.setText(dir.getAbsolutePath());
                settingsManager.save();
            }
        });

        Label formatInfo = new Label("Format: timestamp, port, MAC address, status");
        formatInfo.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");

        enableToggle.setOnAction(e -> {
            boolean enabled = enableToggle.isSelected();
            pathField.setDisable(!enabled);
            browseBtn.setDisable(!enabled);
            settings.setLogFileEnabled(enabled);
            enableToggle.setStyle(enabled ? "-fx-text-fill: #66bb6a;" : "");
            settingsManager.save();
        });

        // Persist path on focus loss to avoid saving on every keystroke
        pathField.textProperty().addListener((obs, old, val) -> settings.setLogFilePath(val));
        pathField.focusedProperty().addListener((obs, was, isFocused) -> {
            if (!isFocused) settingsManager.save();
        });

        pane.getChildren().addAll(
                headingRow,
                enableToggle,
                pathLabel,
                new HBox(8, pathField, browseBtn),
                formatInfo
        );
        return pane;
    }


    // ════════════════════════════════════════════════════════
    // Explore Popular Projects view
    // ════════════════════════════════════════════════════════

    private VBox buildExploreView() {
        Label title = new Label("Explore Popular Projects");
        title.getStyleClass().add("explore-title");

        Label subtitle = new Label("Tap any project to open its GitHub repository.");
        subtitle.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");

        Button backBtn = new Button("← Back to Flasher");
        backBtn.getStyleClass().add("link-button");
        backBtn.setOnAction(e -> showView(flasherView));

        HBox header = new HBox(12, backBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(18);
        content.setPadding(new Insets(4, 4, 16, 4));
        content.getChildren().addAll(header, title, subtitle);

        Map<String, List<FirmwareDefinition>> grouped = groupByCategory();
        for (Map.Entry<String, List<FirmwareDefinition>> entry : grouped.entrySet()) {
            Label catLabel = new Label(entry.getKey());
            catLabel.getStyleClass().add("explore-category");

            FlowPane cards = new FlowPane(12, 12);
            for (FirmwareDefinition fw : entry.getValue()) {
                cards.getChildren().add(buildFirmwareCard(fw));
            }
            content.getChildren().addAll(catLabel, cards);
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox view = new VBox(scroll);
        view.getStyleClass().add("explore-root");
        return view;
    }

    private Node buildFirmwareCard(FirmwareDefinition fw) {
        VBox card = new VBox(8);
        card.getStyleClass().add("firmware-card");
        card.setPrefWidth(210);
        card.setMinWidth(210);
        card.setMaxWidth(210);

        String repo = fw.getGithubRepo();
        String org = repo != null && repo.contains("/") ? repo.split("/")[0] : null;

        ImageView logo = new ImageView();
        logo.setFitWidth(48);
        logo.setFitHeight(48);
        logo.setPreserveRatio(true);

        String localIcon = localLogoFor(fw.getName());
        try {
            if (localIcon != null) {
                var url = getClass().getResource(localIcon);
                if (url != null) logo.setImage(new Image(url.toExternalForm()));
            } else if (org != null) {
                logo.setImage(new Image(
                        "https://github.com/" + org + ".png?size=96",
                        true
                ));
            }
        } catch (Exception ignored) {
        }

        Label name = new Label(fw.getName());
        name.getStyleClass().add("firmware-card-title");
        name.setWrapText(true);

        Label desc = new Label(fw.getDescription());
        desc.getStyleClass().add("firmware-card-desc");
        desc.setWrapText(true);

        HBox header = new HBox(10, logo, name);
        header.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(header, desc);

        card.setOnMouseClicked(e -> {
            String url = (fw.getGithubRepo() != null && !fw.getGithubRepo().isBlank())
                    ? "https://github.com/" + fw.getGithubRepo()
                    : fw.getWebsiteUrl();
            if (url != null && !url.isBlank()) {
                getHostServices().showDocument(url);
            }
        });
        card.setStyle("-fx-cursor: hand;");

        return card;
    }

    private String localLogoFor(String firmwareName) {
        if (firmwareName == null) return null;
        if (firmwareName.startsWith("Tasmota")) return "/icons/firmware/tasmota.png";
        return null;
    }

    private Map<String, List<FirmwareDefinition>> groupByCategory() {
        Map<String, String> categoryOf = new LinkedHashMap<>();
        categoryOf.put("Tasmota", "Smart Home & Automation");
        categoryOf.put("Tasmota SML (ottelo9)", "Smart Home & Automation");

        Map<String, List<FirmwareDefinition>> grouped = new LinkedHashMap<>();
        grouped.put("Smart Home & Automation", new ArrayList<>());
        grouped.put("Other", new ArrayList<>());

        for (FirmwareDefinition fw : FirmwareCatalog.getCatalog()) {
            String cat = categoryOf.getOrDefault(fw.getName(), "Other");
            grouped.get(cat).add(fw);
        }

        grouped.values().removeIf(List::isEmpty);
        return grouped;
    }


    // ════════════════════════════════════════════════════════
    // Prerequisite checks & esptool install
    // ════════════════════════════════════════════════════════

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

    private void autoInstallEsptool() {
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


    // ════════════════════════════════════════════════════════
    // About dialog
    // ════════════════════════════════════════════════════════

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
        email.setOnMouseClicked(e -> getHostServices().showDocument("mailto:hi@ajinkyagokhale.com"));

        Label github = new Label("⚡  github.com/ajinkyagokhale");
        github.getStyleClass().add("about-link");
        github.setOnMouseClicked(e -> getHostServices().showDocument("https://github.com/ajinkyagokhale"));

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


    // ════════════════════════════════════════════════════════
    // Auto-update flow
    // ════════════════════════════════════════════════════════

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


    // ════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════

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

    private void updateChipListForFirmware(FirmwareDefinition def) {
        String previous = chipCombo.getValue();
        chipCombo.getItems().clear();

        if (def == null) {
            chipCombo.getItems().addAll(
                    "auto", "esp32c6", "esp32", "esp32s2",
                    "esp32s3", "esp32c3", "esp32h2", "esp8266"
            );
        } else {
            for (String chip : def.getChipBinMap().keySet()) {
                if (!"default".equals(chip)) chipCombo.getItems().add(chip);
            }
        }

        if (previous != null && chipCombo.getItems().contains(previous)) {
            chipCombo.setValue(previous);
        } else if (!chipCombo.getItems().isEmpty()) {
            chipCombo.getSelectionModel().selectFirst();
        }
    }

    private void refreshPorts() {
        portCombo.getItems().clear();
        List<String> espPorts = PortWatcher.listEsp32Ports();

        if (espPorts.isEmpty()) {
            portCombo.setPromptText("No ESP32 detected...");
        } else {
            portCombo.getItems().addAll(espPorts);
            portCombo.getSelectionModel().selectFirst();
        }
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
        } catch (Exception ignored) {
        }
    }

    private interface Dwmapi extends com.sun.jna.Library {
        Dwmapi INSTANCE = com.sun.jna.Native.load("dwmapi", Dwmapi.class);

        void DwmSetWindowAttribute(com.sun.jna.platform.win32.WinDef.HWND hwnd, int attr,
                                   com.sun.jna.ptr.IntByReference value, int size);
    }
}
