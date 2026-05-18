package com.ajinkyagokhale.espflasher.service;

import java.util.ArrayList;
import java.util.List;

public class PrereqChecker {
    private String pythonCmd;
    private String esptoolCmd;
    private String pipCmd;


    private String findPython() {
        // try full paths first for packaged app
        List<String> candidates = List.of(
                "/opt/homebrew/bin/python3",      // Mac Homebrew
                "/usr/local/bin/python3",          // Mac standard
                "/usr/bin/python3",                // Mac system
                "python3",                         // fallback
                "python",
                "py"
        );

        for (String cmd : candidates) {
            if (runCommand(cmd, "--version") != null) {
                return cmd;
            }
        }
        return null;
    }
    private String findEsptool() {
        // try full python paths first
        List<String> pythonCandidates = List.of(
                "/opt/homebrew/bin/python3",
                "/usr/local/bin/python3",
                "/usr/bin/python3",
                "python3",
                "python"
        );

        for (String python : pythonCandidates) {
            if (runCommand(python, "-m", "esptool", "version") != null) {
                return python + " -m esptool";
            }
        }

        // fallback standalone
        for (String cmd : List.of(
                "/opt/homebrew/bin/esptool.py",
                "/usr/local/bin/esptool.py",
                "esptool.py",
                "esptool"
        )) {
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

        List<String> candidates = List.of(
                "/opt/homebrew/bin/pip3",
                "/usr/local/bin/pip3",
                "/usr/bin/pip3",
                "pip3",
                "pip"
        );

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
    //helper
    public boolean installEsptool() {
        if (pipCmd == null) return false;
        try {
            List<String> cmd = new ArrayList<>(List.of(pipCmd.split(" ")));
            cmd.add("install");
            cmd.add("--break-system-packages");
            cmd.add("esptool");

            System.out.println("Running: " + cmd);  // ← add this

            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            // print output so we can see what happened
            String output = new String(p.getInputStream().readAllBytes());
            System.out.println("Output: " + output);  // ← add this

            p.waitFor();
            checkAll();
            return esptoolCmd != null;
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());  // ← add this
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
