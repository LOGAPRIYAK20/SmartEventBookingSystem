package util;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all plain-text file persistence: users.txt, events.txt,
 * bookings.txt, payments.txt, plus append-only logs.txt / backup.txt
 * for the audit trail described in the project documentation.
 */
public class FileManager {

    private static final String DATA_DIR = "data";

    static {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (IOException e) {
            System.err.println("Could not create data directory: " + e.getMessage());
        }
    }

    private static String path(String fileName) {
        return DATA_DIR + File.separator + fileName;
    }

    /** Overwrites a file with the full current list of records (used to persist state). */
    public static void writeAll(String fileName, List<String> lines) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path(fileName), false))) {
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing " + fileName + ": " + e.getMessage());
        }
    }

    /** Reads every line of a data file; returns empty list if the file does not exist yet. */
    public static List<String> readAll(String fileName) {
        List<String> lines = new ArrayList<>();
        File file = new File(path(fileName));
        if (!file.exists()) return lines;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) lines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Error reading " + fileName + ": " + e.getMessage());
        }
        return lines;
    }

    /** Appends a single audit/log entry with a timestamp -> logs.txt */
    public static synchronized void log(String message) {
        appendLine("logs.txt", "[" + LocalDateTime.now() + "] " + message);
    }

    /** Appends a permanent backup record (bookings/transactions) -> backup.txt */
    public static synchronized void backup(String record) {
        appendLine("backup.txt", "[" + LocalDateTime.now() + "] " + record);
    }

    private static void appendLine(String fileName, String line) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path(fileName), true))) {
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Error appending to " + fileName + ": " + e.getMessage());
        }
    }
}
