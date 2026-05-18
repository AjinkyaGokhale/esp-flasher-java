# Contributing to ESP Flasher

Thanks for your interest in improving ESP Flasher. This document covers how to set up a development environment, the conventions the project follows, and how to get a change merged.

By participating in this project you agree to behave respectfully and constructively in all interactions (issues, pull requests, discussions).

---

## Ways to Contribute

- **Report a bug.** Open a [GitHub issue](../../issues) with reproduction steps, the chip you are flashing, your OS, and the full `esptool` log from the app.
- **Suggest a feature.** Open an issue describing the use case before writing code, so the design can be discussed first.
- **Improve documentation.** README, troubleshooting entries, screenshots, and code comments are all fair game.
- **Submit code.** Bug fixes, new chip support, UI improvements, packaging improvements, tests — all welcome.

---

## Development Setup

### Requirements

- JDK 17 or newer (CI uses JDK 21)
- Maven, or the included `mvnw` wrapper
- Python 3 with `esptool` installed locally (for end-to-end testing)
- An ESP32 / ESP8266 board for hardware testing (recommended)

### Clone and build

```bash
git clone https://github.com/ajinkyagokhale/espflasher.git
cd espflasher
./mvnw clean package
java -jar target/espflasher-1.0.1.jar
```

### Run from an IDE

The main class is `com.ajinkyagokhale.espflasher.EspflasherApplication`. IntelliJ IDEA project files are checked in under `.idea/`; importing the `pom.xml` in any Java IDE will also work.

### Build a native installer locally

```bash
./mvnw jpackage:jpackage
```

The installer is written to `target/dist/`. The active Maven profile (`mac` or `windows`) is selected automatically from your OS.

---

## Project Layout

See [Project Structure](README.md#project-structure) in the README. A short tour of the important files:

| File                            | Responsibility                                                   |
| ------------------------------- | ---------------------------------------------------------------- |
| `ui/FlasherApp.java`            | JavaFX UI, wiring, and listener callbacks                        |
| `service/EsptoolRunner.java`    | Spawning `esptool`, parsing progress, emitting log events        |
| `service/PortWatcher.java`      | Serial port enumeration and Factory Mode device detection        |
| `service/PrereqChecker.java`    | Locating Python / pip / esptool and auto-installing if missing   |
| `model/FlashConfig.java`        | Immutable parameter object for a flash operation                 |
| `listener/`                     | Callback interfaces used to push events to the UI thread         |

---

## Coding Guidelines

- **Language level:** Java 17. Prefer `var`, records, switch expressions, and pattern matching where they improve clarity.
- **UI thread safety:** Anything that touches JavaFX nodes must run on the JavaFX Application Thread. Use `Platform.runLater(...)` from background threads — see the existing listener implementations for the pattern.
- **No blocking on the UI thread.** Long-running work (process spawning, port scanning, `pip install`) must run on a daemon `Thread`, then post results back via `Platform.runLater`.
- **Logging:** Surface user-facing events through the `FlashListener.onLog(...)` callback so they appear in the in-app log. Reserve `System.out` for hard debugging.
- **External commands:** When invoking subprocesses, always set `redirectErrorStream(true)` and read the stream fully to avoid pipe deadlocks (see `PrereqChecker.runCommand`).
- **Resources:** Sounds, icons, and CSS live under `src/main/resources/`. Load them via `getClass().getResource(...)` so they continue to work inside the shaded jar and the jpackage bundle.
- **Style:** 4-space indentation, K&R braces, no wildcard imports outside of JavaFX where they are already used.
- **No new dependencies without discussion.** The project is intentionally small. Open an issue first if you want to add a library.

---

## Commit and Pull Request Workflow

1. **Fork** the repository and create a feature branch:

   ```bash
   git checkout -b feat/my-change
   ```

2. **Make focused commits.** One logical change per commit. The project loosely follows [Conventional Commits](https://www.conventionalcommits.org/):

   ```
   feat: add baud rate auto-negotiation
   fix: handle missing pip on Windows
   chore: bump jSerialComm to 2.11.5
   docs: clarify factory-mode flow
   ci:   pin macos-latest runner
   ```

3. **Test locally.** At minimum:
   - The fat jar launches: `java -jar target/espflasher-1.0.1.jar`
   - A real or stubbed flash completes end-to-end if your change touches the flash path
   - The native installer builds: `./mvnw jpackage:jpackage`

4. **Open a pull request** against `master`. Include:
   - A short description of what changed and why
   - The OS(es) and chip(s) you tested on
   - Screenshots or a screen recording for UI changes
   - Linked issue number(s) if applicable

5. **Review.** Expect at least one round of review. Keep the branch rebased on `master`; avoid merge commits.

---

## Reporting Security Issues

Please do **not** open a public issue for security vulnerabilities. Email the maintainer at `hi@ajinkyagokhale.com` with details and a proof of concept if possible. You will get an acknowledgement within a reasonable timeframe.

---

## Releasing (Maintainers Only)

Releases are cut by pushing a `v*` tag:

```bash
git tag v1.0.2
git push origin v1.0.2
```

The workflow in `.github/workflows/` builds the macOS DMG and Windows MSI and attaches them to a GitHub Release. Bump the version in `pom.xml` (both `<version>` and the `jpackage` `appVersion` / `mainJar`) in the same commit that creates the tag.

---

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE) that covers the project.
