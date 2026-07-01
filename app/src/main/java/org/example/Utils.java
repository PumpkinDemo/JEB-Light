package org.example;

public class Utils {
    public static boolean checkApktoolExists() {
        try {
            ProcessBuilder pb = new ProcessBuilder("apktool", "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean checkJadxExists() {
        try {
            ProcessBuilder pb = new ProcessBuilder("jadx", "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
