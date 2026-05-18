package com.ajinkyagokhale.espflasher.service;

import java.util.ArrayList;
import java.util.List;

public class PrereqChecker {
    private String pythonCmd;
    private String esptoolCmd;
    private String pipCmd;


    private String findPython() {
        for (String cmd : List.of("python3", "python", "py")) {
            if (runCommand(cmd, "--version") != null) {
                return cmd;
            }
        }
        return null;
    }

    private String findEsptool() {
        // try python module first — most reliable
        if (pythonCmd != null) {
            if (runCommand(pythonCmd, "-m", "esptool", "version") != null) {
                return pythonCmd + " -m esptool";
            }
        }

        // fallback — try standalone commands
        for (String cmd : List.of("esptool.py", "esptool")) {
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
        // fallback
        for (String cmd : List.of("pip3", "pip")) {
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
