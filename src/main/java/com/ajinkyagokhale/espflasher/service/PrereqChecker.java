package com.ajinkyagokhale.espflasher.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public class PrereqChecker {
    private static final Preferences PREFS = Preferences.userNodeForPackage(PrereqChecker.class);
    private static final String KEY_PYTHON = "custom.python.path";
    private static final String KEY_ESPTOOL = "custom.esptool.path";

    private String pythonCmd;
    private String esptoolCmd;
    private String pipCmd;

    public static String getCustomPythonPath() {
        return PREFS.get(KEY_PYTHON, "");
    }

    public static String getCustomEsptoolPath() {
        return PREFS.get(KEY_ESPTOOL, "");
    }

    public static void setCustomPaths(String pythonPath, String esptoolPath) {
        PREFS.put(KEY_PYTHON, pythonPath == null ? "" : pythonPath.trim());
        PREFS.put(KEY_ESPTOOL, esptoolPath == null ? "" : esptoolPath.trim());
    }


    private static String quoteIfNeeded(String s) {
        return s.contains(" ") && !s.startsWith("\"") ? "\"" + s + "\"" : s;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static List<String> pythonCandidates() {
        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            String programFiles = System.getenv("ProgramFiles");
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            List<String> list = new ArrayList<>(List.of(
                    "py.exe", "py",
                    "python.exe", "python",
                    "python3.exe", "python3",
                    "C:\\Windows\\py.exe",
                    "C:\\Windows\\System32\\py.exe"
            ));
            for (String ver : List.of("313", "312", "311", "310", "39")) {
                if (localAppData != null) {
                    list.add(localAppData + "\\Programs\\Python\\Python" + ver + "\\python.exe");
                }
                if (programFiles != null) {
                    list.add(programFiles + "\\Python" + ver + "\\python.exe");
                }
                if (programFilesX86 != null) {
                    list.add(programFilesX86 + "\\Python" + ver + "\\python.exe");
                }
                list.add("C:\\Python" + ver + "\\python.exe");
            }
            return list;
        }
        return List.of(
                "/opt/homebrew/bin/python3",
                "/usr/local/bin/python3",
                "/usr/bin/python3",
                "python3",
                "python",
                "py"
        );
    }

    private String findPython() {
        String custom = getCustomPythonPath();
        if (!custom.isEmpty() && runCommand(custom, "--version") != null) {
            return custom;
        }
        for (String cmd : pythonCandidates()) {
            if (runCommand(cmd, "--version") != null) {
                return cmd;
            }
        }
        return null;
    }

    private String findEsptool() {
        String customEsptool = getCustomEsptoolPath();
        if (!customEsptool.isEmpty() && runCommand(customEsptool, "version") != null) {
            return customEsptool;
        }

        String customPython = getCustomPythonPath();
        if (!customPython.isEmpty() && runCommand(customPython, "-m", "esptool", "version") != null) {
            return customPython + " -m esptool";
        }

        for (String python : pythonCandidates()) {
            if (runCommand(python, "-m", "esptool", "version") != null) {
                return quoteIfNeeded(python) + " -m esptool";
            }
        }

        List<String> standalone = isWindows()
                ? List.of("esptool.exe", "esptool.py", "esptool")
                : List.of("/opt/homebrew/bin/esptool.py", "/usr/local/bin/esptool.py", "esptool.py", "esptool");
        for (String cmd : standalone) {
            if (runCommand(cmd, "version") != null) {
                return cmd;
            }
        }

        return null;
    }

    private String findPip() {
        if (pythonCmd != null) {
            if (runCommand(pythonCmd, "-m", "pip", "--version") != null) {
                return pythonCmd + " -m pip";
            }
        }

        List<String> candidates = isWindows()
                ? List.of("pip", "pip3")
                : List.of("/opt/homebrew/bin/pip3", "/usr/local/bin/pip3", "/usr/bin/pip3", "pip3", "pip");

        for (String cmd : candidates) {
            if (runCommand(cmd, "--version") != null) {
                return cmd;
            }
        }

        return null;
    }

    public void checkAll() {
        pythonCmd = findPython();
        esptoolCmd = findEsptool();
        pipCmd = findPip();
    }

    public String getPythonCmd()   { return pythonCmd; }
    public String getEsptoolCmd()  { return esptoolCmd; }
    public String getPipCmd()      { return pipCmd; }
    public boolean isReady() {
        return esptoolCmd != null;
    }
    public boolean installEsptool() {
        return installEsptool(null);
    }

    public boolean installEsptool(Consumer<String> onLine) {
        if (pipCmd == null) return false;
        try {
            List<String> cmd = new ArrayList<>(List.of(pipCmd.split(" ")));
            cmd.add("install");
            cmd.add("--break-system-packages");
            cmd.add("esptool");

            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (onLine != null) onLine.accept(line);
                }
            }
            p.waitFor();
            checkAll();
            return esptoolCmd != null;
        } catch (Exception e) {
            return false;
        }
    }
    private String runCommand(String... args) {
        try {
            Process p = new ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(p.getInputStream().readAllBytes()).strip();
            int exit = p.waitFor();
            return (exit == 0 && !output.isEmpty()) ? output : null;
        } catch (Exception e) {
            return null;
        }
    }
}
