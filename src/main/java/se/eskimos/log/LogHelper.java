package se.eskimos.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import se.eskimos.recorder.MailExceptionBuffer;

public class LogHelper {
    private static String logFile = null;
    private static ZoneId logTimeZone = ZoneId.systemDefault();
    private static BufferedWriter fileWriter = null;
    private static final Object lock = new Object();
    
    // Static formatter for efficient timestamp generation
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Call this at startup to initialize logging from config.
     * @param configPath Path to config.properties
     */
    public static void init(String configPath) {
        // 1. Check environment variable
        String envLogFile = System.getenv("LOGFILE");
        if (envLogFile != null && !envLogFile.trim().isEmpty()) {
            setLogFile(envLogFile);
            return;
        }
        // 2. Check config.properties
        if (configPath != null) {
            try (var fis = new java.io.FileInputStream(configPath)) {
                Properties prop = new Properties();
                prop.load(fis);
                String configLogFile = prop.getProperty("logFile");
                if (configLogFile != null && !configLogFile.trim().isEmpty()) {
                    setLogFile(configLogFile);
                    return;
                }
            } catch (IOException e) {
                // Fallback: Set a log file
                setLogFile("logs/iptv-recorder.log");
            }
        }
    }

    /**
     * Sets the log file path and initializes the file writer
     * @param file Path to the log file
     */
    public static void setLogFile(String file) {
        logFile = file;
        try {
            if (fileWriter != null) {
                fileWriter.close();
            }
        } catch (Exception e) {
            // Ignore close errors
        }
        fileWriter = null;
        // Only create parent directory if it is explicitly part of the logFile path
        if (logFile != null && !logFile.trim().isEmpty()) {
            try {
                File logDir = new File(logFile).getParentFile();
                if (logDir != null && !logDir.exists() && !logDir.getPath().equals(".")) {
                    logDir.mkdirs();
                }
                fileWriter = new BufferedWriter(new FileWriter(logFile, true));
            } catch (IOException e) {
                fileWriter = null;
                System.err.println("[LogHelper][ERROR] Failed to open log file '" + logFile + "': " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Sets the timezone for log timestamps
     * @param zone The timezone to use
     */
    public static void setTimeZone(ZoneId zone) {
        logTimeZone = zone;
    }

    /**
     * Gets the current timezone used for log timestamps
     * @return The current timezone
     */
    public static ZoneId getTimeZone() {
        return logTimeZone;
    }

    /**
     * Generates a timestamp string for the current time
     * @return Formatted timestamp string
     */
    private static String getTimestamp() {
        ZonedDateTime now = ZonedDateTime.now(logTimeZone);
        return String.format("[%s]", TIMESTAMP_FORMATTER.format(now));
    }

    /**
     * Formats a log message with timestamp and level
     * @param level The log level
     * @param message The log message
     * @return Formatted log message
     */
    private static String formatMessage(String level, String message) {
        return String.format("%s [%s] %s", getTimestamp(), level, message);
    }

    /**
     * Logs an INFO level message
     * @param message The message to log
     */
    public static void Log(String message) {
        logInternal("INFO", message);
    }

    /**
     * Logs a DEBUG level message
     * @param message The message to log
     */
    public static void LogDebug(String message) {
        logInternal("DEBUG", message);
    }

    /**
     * Logs a WARNING level message
     * @param message The message to log
     */
    public static void LogWarning(String message) {
        logInternal("WARNING", message);
    }

    /**
     * Logs an ERROR level message
     * @param message The message to log
     */
    public static void LogError(String message) {
        logInternal("ERROR", message);
        MailExceptionBuffer.addException("LogHelper.LogError", message);
    }

    /**
     * Logs an ERROR level message
     * @param message The message to log
     * @param e The exception to log
     */
    public static void LogError(String message, Exception e) {
        logInternal("ERROR", message);
        LogCritical(message + "\n" + printStackTrace(e));
        MailExceptionBuffer.addException("LogHelper.LogError", e);
    }

    /**
     * Logs a scheduler-specific INFO message
     * @param message The message to log
     */
    public static void LogScheduler(String message) {
        logInternal("INFO", "[SCHEDULER] " + message);
    }

    /**
     * Internal method to write log messages to file
     * @param level The log level
     * @param message The log message
     */
    private static void logInternal(String level, String message) {
        String formatted = formatMessage(level, message);
        boolean fileLogged = false;
        synchronized (lock) {
            if (fileWriter != null) {
                try {
                    fileWriter.write(formatted);
                    fileWriter.newLine();
                    fileWriter.flush();
                    fileLogged = true;
                } catch (IOException e) {
                    System.err.println("[LogHelper] Failed to write to log file: " + e.getMessage());
                    System.err.println(formatted);
                }
            }
        }
        // Om fil-loggning misslyckades, logga alltid till System.err
        if (!fileLogged) {
            System.err.println(formatted);
        }
    }

    /**
     * Loggar ett kritiskt fel till både loggfil och terminal
     */
    public static void LogCritical(String message) {
        String formatted = formatMessage("CRITICAL", message);
        synchronized (lock) {
            if (fileWriter != null) {
                try {
                    fileWriter.write(formatted);
                    fileWriter.newLine();
                    fileWriter.flush();
                } catch (IOException e) {
                    // Ignorera, logga ändå till terminal
                }
            }
        }
        System.err.println(formatted);
    }

    /**
     * Converts an exception stack trace to a string
     * @param e The exception to convert
     * @return String representation of the stack trace
     */
    public static String printStackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Closes the log file writer. Call this during application shutdown.
     */
    public static void close() {
        synchronized (lock) {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    System.err.println("[LogHelper] Error closing log file: " + e.getMessage());
                } finally {
                    fileWriter = null;
                }
            }
        }
    }
} 