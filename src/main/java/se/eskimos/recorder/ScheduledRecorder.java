package se.eskimos.recorder;

import se.eskimos.log.LogHelper;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.time.ZoneId;

import se.eskimos.helpers.StringAndFileHelper;
import se.eskimos.helpers.UserIOHelper;
import se.eskimos.helpers.TextHelper;
import se.eskimos.helpers.RecorderHelper;
import se.eskimos.m3u.M3UHolder;
import se.eskimos.helpers.DateTimeHelper;

public class ScheduledRecorder {

    public static void main(String[] args) {
        // Set global UncaughtExceptionHandler
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                LogHelper.LogCritical("[SCHEDULER] Uncaught throwable in thread " + t.getName() + ": " + LogHelper.printStackTrace(e));
            } catch (Throwable ex) {
                System.err.println("[SCHEDULER] Uncaught throwable in thread " + t.getName() + ": " + e.getMessage());
                e.printStackTrace(System.err);
            }
        });
        if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.println("Usage: java -cp ... ScheduledRecorder <url> <outputPath> <startTime> <stopTime> <ffmpeg|regular> <logConfigPath> <tvgName> [timezone] [is24Hour] [logFile] [groupTitle] [tvgId]\n" +
                "All configuration must be passed as arguments. This program does NOT read config.properties.\n" +
                "See README.md for details.");
            return;
        }
        if (args.length < 7) {
            System.err.println("Usage: java -cp ... ScheduledRecorder <url> <outputPath> <startTime> <stopTime> <ffmpeg|regular> <logConfigPath> <tvgName> [timezone] [is24Hour] [logFile] [groupTitle] [tvgId]");
            return;
        }
        String url = args[0];
        String outputPath = args[1];
        String startTime = args[2]; // HH:mm
        String stopTime = args[3];  // HH:mm
        String mode = args[4];      // ffmpeg or regular
        String logConfigPath = args[5];
        String tvgName = args.length > 6 ? args[6] : "";
        String timezone = args.length > 7 ? args[7] : "Europe/Stockholm";
        boolean is24Hour = args.length > 8 ? Boolean.parseBoolean(args[8]) : true;
        String logFile = args.length > 9 ? args[9] : null;
        String groupTitle = args.length > 10 ? args[10] : "";
        String tvgId = args.length > 11 ? args[11] : "";
        Integer recRetries = Integer.parseInt(args[12]);
        Integer recRetriesDelay = Integer.parseInt(args[13]); // Ta bort * 1000
        String tvgLogo = args.length > 14 ? args[14] : "";
        // Build channelInfo with correct tvgName and groupTitle
        M3UHolder channelInfo = new M3UHolder(groupTitle, url, tvgName, groupTitle, tvgId, tvgName, tvgLogo);
        // Determine displayName (tvgName or name as fallback)
        String displayName = (tvgName != null && !tvgName.isEmpty()) ? tvgName : channelInfo.name();

        // Only set log file if provided as argument
        if (logFile != null && !logFile.isEmpty()) {
            LogHelper.setLogFile(logFile);
            LogHelper.Log(String.format(TextHelper.SCHEDULER_LOGGING_STARTED, logFile));
        } else {
            System.err.println("[SCHEDULER] Logging only to terminal. No log file specified.");
        }
        ZoneId zone = ZoneId.of(timezone);
        LogHelper.setTimeZone(zone);
       
        // Wait until start time before attempting any connections
        DateTimeFormatter formatter = is24Hour ? DateTimeFormatter.ofPattern("HH:mm") : DateTimeFormatter.ofPattern("hh:mm a");
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalTime startLocal = DateTimeHelper.parseFlexibleLocalTime(startTime, formatter);
        ZonedDateTime start = now.withHour(startLocal.getHour()).withMinute(startLocal.getMinute()).withSecond(0).withNano(0);
        if (start.isBefore(now)) {
            LogHelper.LogWarning(String.format(TextHelper.SCHEDULER_START_TIME_PASSED, startTime));
        } else {
            long millisToWait = java.time.Duration.between(now, start).toMillis();
            LogHelper.Log(String.format(TextHelper.SCHEDULER_WAITING_UNTIL_START, (millisToWait/1000), startTime));
            try { Thread.sleep(millisToWait); } catch (InterruptedException ie) { /* ignore */ }
        }
       
        // Startup connection retry mechanism: uses configurable recRetries and recRetriesDelay
        boolean connectionEstablished = false;
        
        for (int attempt = 1; attempt <= recRetries; attempt++) {
            try {
                LogHelper.Log(String.format("[STARTUP] Connection attempt %d/%d to URL: %s", attempt, recRetries, url));
                java.net.URL urlObj = java.net.URI.create(url).toURL();
                java.net.URLConnection conn = urlObj.openConnection();
                conn.setConnectTimeout(10000); // 10 second timeout
                conn.setReadTimeout(10000); // 10 second timeout
                conn.connect();
                conn.getInputStream().close(); // Test the connection and close immediately
                LogHelper.Log(String.format("[STARTUP] Connection successful on attempt %d/%d", attempt, recRetries));
                connectionEstablished = true;
                break;
            } catch (Exception e) {
                if (attempt < recRetries) {
                    LogHelper.LogWarning(String.format("[STARTUP] Connection attempt %d/%d failed: %s. Retrying in %d seconds...", attempt, recRetries, e.getMessage(), recRetriesDelay));
                    try {
                        Thread.sleep(recRetriesDelay * 1000);
                    } catch (InterruptedException ie) {
                        LogHelper.LogError("[STARTUP] Retry delay interrupted: " + ie.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    LogHelper.LogError(String.format("[STARTUP] All %d connection attempts failed. Last error: %s", recRetries, e.getMessage()));
                    LogHelper.LogError("[STARTUP] Unable to establish connection, exiting.");
                    shutdownAndExit(1);
                }
            }
        }
        
        if (!connectionEstablished) {
            LogHelper.LogError("[STARTUP] Failed to establish connection after all retry attempts.");
            shutdownAndExit(1);
        }
       
        RecorderHelper helper = null;
        LocalTime stop = DateTimeHelper.parseFlexibleLocalTime(stopTime, formatter);
       
        if ("ffmpeg".equalsIgnoreCase(mode)) {
            helper = new RecorderHelper(new UserIOHelper(new java.util.Scanner(System.in), System.out));
            helper.setUrl(url);
            helper.setTimeFrom(startTime);
            helper.setTimeTo(stopTime);
            helper.setChannelInfo(channelInfo);
            helper.setLogConfigPath(logConfigPath);
            helper.setTimezone(timezone);
            helper.setIs24Hour(is24Hour);
            helper.setLogFile(logFile);
            helper.setGroupTitle(groupTitle);
            helper.setTvgId(tvgId);
            helper.setRecRetries(recRetries);
            helper.setRecRetriesDelay(recRetriesDelay / 1000); // save in seconds
            helper.setTvgLogo(tvgLogo);
            helper.setTvgName(groupTitle);
            int retryCount = 0;
            boolean started = false;
            while (retryCount < recRetries && !started) {
                try {
                    LogHelper.Log(String.format(TextHelper.SCHEDULER_ATTEMPTING_START, (retryCount+1), recRetries, displayName));
                    helper.startRecFFMPEG(outputPath); // If getLogo is called, pass displayName as channelName
                    started = true;
                } catch (Exception e) {
                    retryCount++;
                    started = false;
                    if (retryCount < recRetries) {
                        LogHelper.LogWarning(String.format(TextHelper.SCHEDULER_FAILED_START_FFMPEG, retryCount, recRetries, displayName, recRetriesDelay, LogHelper.printStackTrace(e)));
                        try { Thread.sleep(recRetriesDelay * 1000); } catch (Exception t) { LogHelper.LogError(TextHelper.SCHEDULER_ERROR_WAITING_BETWEEN_ATTEMPTS + LogHelper.printStackTrace(t)); }
                    } else {
                        LogHelper.LogError(String.format(TextHelper.SCHEDULER_COULD_NOT_START_FFMPEG, recRetries, displayName, LogHelper.printStackTrace(e)));
                        LogHelper.LogError(TextHelper.SCHEDULER_PROCESS_EXITING);
                        shutdownAndExit(1);
                    }
                }
            }
            if (started) {
                try {
                    LogHelper.Log(String.format(TextHelper.SCHEDULER_RECORDING_IN_PROGRESS, stopTime));
                    while (true) {
                        LocalTime nowTime = LocalTime.now(zone);
                        long secondsLeft = java.time.Duration.between(nowTime, stop).getSeconds();
                        if (secondsLeft <= 0) break;
                        Thread.sleep(1000);
                    }
                    LogHelper.Log(TextHelper.SCHEDULER_STOP_TIME_REACHED);
                    helper.stopRecording();
                    // Wait for executor to finish
                    shutdownAndExit(0);
                } catch (Exception e) {
                    LogHelper.LogError(TextHelper.SCHEDULER_ERROR_DURING_STOP + LogHelper.printStackTrace(e));
                    LogHelper.LogError(TextHelper.SCHEDULER_PROCESS_EXITING);
                    shutdownAndExit(1);
                }
            } else {
                LogHelper.LogError(TextHelper.SCHEDULER_RECORDING_COULD_NOT_BE_STARTED);
                LogHelper.LogError(TextHelper.SCHEDULER_PROCESS_EXITING);
                shutdownAndExit(1);
            }
        } else {
            // Regular-mode
            int retryCount = 0;
            boolean started = false;
            String sanitizedChannel = null;
            while (retryCount < recRetries && !started) {
                try {
                    RecorderHelper helperReg = createConfiguredHelperReg(
                        url, startTime, stopTime, channelInfo, logConfigPath, timezone, is24Hour, logFile, groupTitle, tvgId, recRetries, recRetriesDelay, tvgLogo, groupTitle
                    );
                    sanitizedChannel = StringAndFileHelper.sanitizeForFileName(groupTitle);
                    // Determine display name for channel (prefer tvgName, fallback to name)
                    displayName = (channelInfo.tvgName() != null && !channelInfo.tvgName().isEmpty()) ? channelInfo.tvgName() : channelInfo.name();
                    java.util.concurrent.Future<?> recFuture = java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
                        try {
                            helperReg.startRecRegular(outputPath); // If getLogo is called, pass displayName as channelName
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    started = true;
                    LogHelper.Log(String.format(TextHelper.SCHEDULER_RECORDING_STARTED, displayName, startTime, stopTime));
                    // Wait for the recording to finish or fail
                    while (true) {
                        LocalTime nowTime = LocalTime.now(zone);
                        long secondsLeft = java.time.Duration.between(nowTime, stop).getSeconds();
                        if (secondsLeft <= 0) break;
                        if (recFuture.isDone()) break;
                        try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                    }
                    if (!recFuture.isDone()) {
                        LogHelper.Log(TextHelper.SCHEDULER_STOP_TIME_REACHED_ATTEMPTING_CANCEL);
                        recFuture.cancel(true);
                        LogHelper.Log(TextHelper.SCHEDULER_REGULAR_RECORDING_CANCELLED);
                    } else {
                        // Check if the recording thread threw an exception
                        try {
                            recFuture.get();
                        } catch (Exception e) {
                            throw e.getCause() != null ? new Exception(e.getCause()) : e;
                        }
                    }
                    shutdownAndExit(0);
                } catch (Exception e) {
                    retryCount++;
                    started = false;
                    if (retryCount < recRetries) {
                        LogHelper.LogWarning(String.format(TextHelper.SCHEDULER_FAILED_START_REGULAR, retryCount, recRetries, displayName, recRetriesDelay, LogHelper.printStackTrace(e)));
                        try { Thread.sleep(recRetriesDelay * 1000); } catch (Exception t) { LogHelper.LogError(TextHelper.SCHEDULER_ERROR_WAITING_BETWEEN_ATTEMPTS + LogHelper.printStackTrace(t)); }
                    } else {
                        LogHelper.LogError(String.format(TextHelper.SCHEDULER_COULD_NOT_START_REGULAR, recRetries, displayName, LogHelper.printStackTrace(e)));
                        // Try to delete the created folder if empty
                        try {
                            String channelNameToDelete = sanitizedChannel != null ? sanitizedChannel : groupTitle;
                            java.io.File channelDir = new java.io.File(outputPath, channelNameToDelete);
                            if (channelDir.exists() && channelDir.isDirectory() && channelDir.list().length == 0) {
                                if (channelDir.delete()) {
                                    LogHelper.Log(String.format(TextHelper.SCHEDULER_DELETED_EMPTY_RECORDING_FOLDER, channelDir.getAbsolutePath()));
                                } else {
                                    LogHelper.LogWarning(String.format(TextHelper.SCHEDULER_COULD_NOT_DELETE_EMPTY_RECORDING_FOLDER, channelDir.getAbsolutePath()));
                                }
                            }
                        } catch (Exception delEx) {
                            LogHelper.LogWarning(TextHelper.SCHEDULER_EXCEPTION_DELETING_EMPTY_RECORDING_FOLDER + LogHelper.printStackTrace(delEx));
                        }
                        LogHelper.Log(TextHelper.SCHEDULER_PROCESS_ENDED_NO_RECORDING);
                        LogHelper.LogError(TextHelper.SCHEDULER_PROCESS_EXITING);
                        shutdownAndExit(1);
                    }
                }
            }
        }
        // Start a failsafe timer thread to exit at stop time
        Thread stopTimer = new Thread(() -> {
            try {
                // Use already declared formatter and stop
                LocalTime stopLocal = DateTimeHelper.parseFlexibleLocalTime(stopTime, formatter);
                ZonedDateTime stopDateTime = ZonedDateTime.now(zone);
                if (stopDateTime.isBefore(stopDateTime.withHour(stopLocal.getHour()).withMinute(stopLocal.getMinute()).withSecond(0).withNano(0))) {
                    stopDateTime = stopDateTime.plusDays(1);
                }
                while (true) {
                    ZonedDateTime current = ZonedDateTime.now(zone);
                    
                    if (!current.isBefore(stopDateTime)) {
                        LogHelper.Log(TextHelper.SCHEDULER_FAILSAFE_TIMER_REACHED_STOP_TIME);
                        shutdownAndExit(0);
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                LogHelper.LogError(TextHelper.SCHEDULER_FAILSAFE_TIMER_ERROR + LogHelper.printStackTrace(e));
                LogHelper.LogError(TextHelper.SCHEDULER_FAILSAFE_TIMER_EXITING);
                shutdownAndExit(1);
            }
        });
        stopTimer.setDaemon(true);
        stopTimer.start();
    }

    // Create and configure RecorderHelper for REGULAR-mode
    private static RecorderHelper createConfiguredHelperReg(String url, String startTime, String stopTime, M3UHolder channelInfo, String logConfigPath, String timezone, boolean is24Hour, String logFile, String groupTitle, String tvgId, int recRetries, int recRetriesDelay, String tvgLogo, String tvgName) {
        RecorderHelper helperReg = new RecorderHelper(new UserIOHelper(new java.util.Scanner(System.in), System.out));
        helperReg.setUrl(url);
        helperReg.setTimeFrom(startTime);
        helperReg.setTimeTo(stopTime);
        helperReg.setChannelInfo(channelInfo);
        helperReg.setLogConfigPath(logConfigPath);
        helperReg.setTimezone(timezone);
        helperReg.setIs24Hour(is24Hour);
        helperReg.setLogFile(logFile);
        helperReg.setGroupTitle(groupTitle);
        helperReg.setTvgId(tvgId);
        helperReg.setRecRetries(recRetries);
        helperReg.setRecRetriesDelay(recRetriesDelay / 1000); // save in seconds
        helperReg.setTvgLogo(tvgLogo);
        helperReg.setTvgName(tvgName);
        return helperReg;
    }

    // Utility method to shutdown executor and exit process
    private static void shutdownAndExit(int exitCode) {
        try {
            java.util.concurrent.ExecutorService exec = RecorderHelper.getExecutor();
            exec.shutdown();
            if (!exec.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                LogHelper.LogError("[SCHEDULER] Executor did not terminate within timeout, forcing shutdownNow.");
                exec.shutdownNow();
            }
        } catch (Exception ex) {
            LogHelper.LogError("[SCHEDULER] Error during executor shutdown: " + LogHelper.printStackTrace(ex));
        }
        System.exit(exitCode);
    }

    // Build argument list for ScheduledRecorder process
    public static java.util.List<String> buildScheduledRecorderArgs(
        String url, String filePath, String timeFrom, String timeTo, String mode, String logConfigPath, String tvgName, String timezone, boolean is24Hour, String logFile, String groupTitle, String tvgId, int recRetries, int recRetriesDelay, String tvgLogo
    ) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(url);
        args.add(filePath);
        args.add(timeFrom);
        args.add(timeTo);
        args.add(mode);
        args.add(logConfigPath != null ? logConfigPath : "");
        args.add(tvgName != null ? tvgName : "");
        args.add(timezone != null ? timezone : "Europe/Stockholm");
        args.add(Boolean.toString(is24Hour));
        args.add(logFile != null ? logFile : "");
        args.add(groupTitle != null ? groupTitle : "");
        args.add(tvgId != null ? tvgId : "");
        args.add(Integer.toString(recRetries));
        args.add(Integer.toString(recRetriesDelay));
        args.add(tvgLogo != null ? tvgLogo : "");
        return args;
    }
} 