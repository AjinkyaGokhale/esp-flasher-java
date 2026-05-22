# ESP Flasher — GUI Firmware Flashing Tool for ESP32 and ESP8266

**ESP Flasher** is a free, open-source desktop app for flashing firmware to Espressif **ESP32**, **ESP32-S2/S3/C3/C6/H2**, and **ESP8266** microcontrollers. It wraps the official [`esptool`](https://github.com/espressif/esptool) in a native JavaFX GUI — no command line needed.

Works on **macOS** (Apple Silicon + Intel) and **Windows**. Flash **Tasmota**, **ESPHome**, **WLED**, **MicroPython**, **Arduino**, and custom ESP-IDF firmware from a single `.bin` file. A modern alternative to **Tasmotizer** and the **Espressif Flash Download Tool** that runs natively on Mac.

![ESP Flasher main window on macOS — flashing an ESP32 firmware binary](docs/screenshots/main.png)

---

## Features

- Native `.dmg` and `.msi` installers — no Python GUI required to launch
- Automatic ESP32/ESP8266 serial port detection (CP210x, CH340, FTDI)
- Selectable chip type, baud rate up to 921600, and custom flash offset
- Live progress bar and real-time `esptool` log output
- **Factory Mode** — auto-flashes every ESP board the moment it is plugged in
- One-click `esptool` install via `pip` if it is missing
- Automatic light / dark theme based on system appearance
- Persistent flash counter for production runs
- 100% open source — MIT license, no telemetry

---

## Download

| Platform | Installer |
| -------- | --------- |
| macOS (Apple Silicon & Intel) | [ESP.Flasher-1.0.3.dmg](https://github.com/AjinkyaGokhale/esp-flasher-java/releases/download/v1.0.3/ESP.Flasher-1.0.3.dmg) |
| Windows | [ESP.Flasher-1.0.3.msi](https://github.com/AjinkyaGokhale/esp-flasher-java/releases/download/v1.0.3/ESP.Flasher-1.0.3.msi) |

**macOS:** Drag to Applications. If Gatekeeper blocks it:
```bash
xattr -cr "/Applications/ESP Flasher.app"
```

**Windows:** Run the `.msi`. If SmartScreen warns you, choose **More info → Run anyway**.

Both installers are currently unsigned.

---

## Prerequisites

- **Python 3** — required by `esptool`. Install from [python.org](https://python.org) or Homebrew.
- **`esptool`** — the app detects it and offers to install via `pip` on first launch.
- **USB drivers** — boards with CP210x or CH340 USB-to-serial chips may need vendor drivers on Windows and older macOS.

---

## How to Flash ESP32 or ESP8266 Firmware

1. Click **Browse...** and pick your firmware `.bin` file (Tasmota, ESPHome, WLED, MicroPython, Arduino, or ESP-IDF).
2. Select the **Chip** (or leave on `auto` to let `esptool` detect it).
3. Select the **Port** — click **Refresh** if your board isn't listed.
4. Choose a **Baud Rate** — `460800` is a safe default; `921600` is faster if your USB-serial chip supports it.
5. Set the **Flash Offset**: `0x0` for merged ESP32 binaries and all ESP8266, `0x1000` for ESP32 bootloader-only, `0x10000` for application-only.
6. Click **Flash Once**.

### Factory Mode — Mass Flash ESP Boards

For flashing the same firmware onto many boards in sequence (production runs, classroom kits, repair shops):

1. Select firmware, chip, baud rate, and offset.
2. Click **Factory Mode** — the app waits for a device.
3. Plug in a board. It flashes automatically.
4. Unplug when done (green bar + chime), plug in the next one.
5. Click **Stop Factory** when finished. The **Flashed:** counter tracks your total.

---

## Supported Espressif Chips

`auto` (autodetect), **ESP32**, **ESP32-S2**, **ESP32-S3**, **ESP32-C3**, **ESP32-C6**, **ESP32-H2**, **ESP8266**.

Works with NodeMCU, Wemos D1 Mini, ESP32 DevKitC, ESP32-S3 DevKitC, ESP32-C3 SuperMini, M5Stack, Seeed XIAO ESP32, LilyGO T-Display, Adafruit Feather ESP32, Sonoff Basic/Mini, and most generic Espressif modules.

---

## Building From Source

Requires JDK 17+ and Maven (or the included `mvnw` wrapper).

```bash
# Run from source
./mvnw clean package
java -jar target/espflasher-1.0.3.jar

# Build native installer (DMG on macOS, MSI on Windows)
./mvnw clean package
./mvnw jpackage:jpackage
# Output: target/dist/
```

---

## Troubleshooting

| Symptom | Fix |
| ------- | --- |
| "Python not found" | Install Python 3 from [python.org](https://python.org) and restart. |
| "esptool not found" | Click the status label, or run `python3 -m pip install esptool`. |
| No ports listed | Install your board's USB-serial driver (CP210x, CH340, FTDI), then click **Refresh**. |
| "Timed out connecting to ESP" | Hold BOOT while plugging in. Try a lower baud rate (e.g. `115200`). |
| macOS: "App is damaged and can't be opened" | `xattr -cr "/Applications/ESP Flasher.app"` |
| Firmware doesn't run after flash | Check the flash offset — many ESP32 images need `0x1000` or `0x10000`. |

The full `esptool` output is visible in the log area — include it when filing an issue.

---

## Contributing

Open an [issue](../../issues) before starting work. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup and conventions.

## License

[MIT](LICENSE) — © 2026 Ajinkya Gokhale

## Credits

- [Espressif Systems](https://github.com/espressif/esptool) — `esptool`
- [Fazecast](https://github.com/Fazecast/jSerialComm) — `jSerialComm`
- [OpenJFX](https://openjfx.io) — JavaFX runtime

---

*Keywords: ESP32 flasher, ESP8266 flasher, esptool GUI, flash ESP32 firmware Mac, Tasmotizer alternative macOS, Tasmota flasher, ESPHome flash tool, WLED flasher, ESP32-C3 flasher, ESP32-S3 flasher, factory flash ESP32, mass flashing tool, ESP firmware upload tool.*
