package se.eskimos.recorder;

import se.eskimos.log.LogHelper;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.time.ZoneId;

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
        // Bestäm displayName (tvgName eller name som fallback)
        String displayName = (tvgName != null && !tvgName.isEmpty()) ? tvgName : channelInfo.name();
        // Läs isResume-flagga sist (default false om saknas)
        final boolean isResume = (args.length > 15) && Boolean.parseBoolean(args[15]);
        // Only set log file if provided as argument
        if (logFile != null && !logFile.isEmpty()) {
            LogHelper.setLogFile(logFile);
            if (!isResume) {
                LogHelper.Log("[SCHEDULER] Logging started. Log file: " + logFile);
            }
        } else {
            System.err.println("[SCHEDULER] Logging only to terminal. No log file specified.");
        }
        ZoneId zone = ZoneId.of(timezone);
        LogHelper.setTimeZone(zone);
       
        RecorderHelper helper = null;
        DateTimeFormatter formatter = is24Hour ? DateTimeFormatter.ofPattern("HH:mm") : DateTimeFormatter.ofPattern("hh:mm a");
        LocalTime stop = LocalTime.parse(stopTime, formatter);
        // Wait until start time before starting any recording (applies to both modes)
        if (!isResume) {
            ZonedDateTime now = ZonedDateTime.now(zone);
            LocalTime startLocal = LocalTime.parse(startTime, formatter);
            ZonedDateTime start = now.withHour(startLocal.getHour()).withMinute(startLocal.getMinute()).withSecond(0).withNano(0);
            if (start.isBefore(now)) {
                LogHelper.LogWarning("[SCHEDULER] Start time '" + startTime + "' has already passed, starting immediately.");
            } else {
                long millisToWait = java.time.Duration.between(now, start).toMillis();
                LogHelper.Log("[SCHEDULER] Waiting " + (millisToWait/1000) + " seconds until start time " + startTime);
                try { Thread.sleep(millisToWait); } catch (InterruptedException ie) { /* ignore */ }
            }
        }
       
        if ("ffmpeg".equalsIgnoreCase(mode)) {
            helper = new RecorderHelper(new UserInputHelper(new java.util.Scanner(System.in), System.out));
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
                    LogHelper.Log("[SCHEDULER] Attempting to start recording (attempt " + (retryCount+1) + "/" + recRetries + ") for channel: " + displayName);
                    helper.startRecFFMPEG(outputPath, isResume); // If getLogo is called, pass displayName as channelName
                    started = true;
                } catch (Exception e) {
                    retryCount++;
                    started = false;
                    if (retryCount < recRetries) {
                        LogHelper.LogWarning("[SCHEDULER] Failed to start FFMPEG recording (attempt " + retryCount + "/" + recRetries + ") for channel: " + displayName + ". Retrying in " + (recRetriesDelay/1000) + " seconds. Error: " + LogHelper.printStackTrace(e));
                        try { Thread.sleep(recRetriesDelay); } catch (Exception t) { LogHelper.LogError("[SCHEDULER] Error while waiting between FFMPEG attempts: " + LogHelper.printStackTrace(t)); }
                    } else {
                        LogHelper.LogError("[SCHEDULER] Could not start FFMPEG recording after " + recRetries + " attempts for channel: " + displayName + ". Error: " + LogHelper.printStackTrace(e));
                        LogHelper.LogError("[SCHEDULER] Process exiting with exit(1)");
                        shutdownAndExit(1);
                    }
                }
            }
            if (started) {
                try {
                    LogHelper.Log("[SCHEDULER] Recording in progress, waiting until stop time " + stopTime + "...");
                    while (true) {
                        LocalTime nowTime = LocalTime.now(zone);
                        long secondsLeft = java.time.Duration.between(nowTime, stop).getSeconds();
                        if (secondsLeft <= 0) break;
                        Thread.sleep(1000);
                    }
                    LogHelper.Log("[SCHEDULER] Stop time reached. Stopping recording.");
                    helper.stopRecording();
                    // Wait for executor to finish
                    shutdownAndExit(0);
                } catch (Exception e) {
                    LogHelper.LogError("[SCHEDULER] Error during recording stop: " + LogHelper.printStackTrace(e));
                    LogHelper.LogError("[SCHEDULER] Process exiting with exit(1)");
                    shutdownAndExit(1);
                }
            } else {
                LogHelper.LogError("[SCHEDULER] Recording could not be started. Process exiting with exit(1)");
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
                    sanitizedChannel = RecorderHelper.sanitizeForFileName(groupTitle);
                    // Determine display name for channel (prefer tvgName, fallback to name)
                    displayName = (channelInfo.tvgName() != null && !channelInfo.tvgName().isEmpty()) ? channelInfo.tvgName() : channelInfo.name();
                    java.util.concurrent.Future<?> recFuture = java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
                        try {
                            helperReg.startRecRegular(outputPath, isResume); // If getLogo is called, pass displayName as channelName
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    started = true;
                    if (!isResume) {
                        LogHelper.Log("[SCHEDULER] Started recording for channel: " + displayName);
                    }
                    // Wait for the recording to finish or fail
                    while (true) {
                        LocalTime nowTime = LocalTime.now(zone);
                        long secondsLeft = java.time.Duration.between(nowTime, stop).getSeconds();
                        if (secondsLeft <= 0) break;
                        if (recFuture.isDone()) break;
                        try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                    }
                    if (!recFuture.isDone()) {
                        LogHelper.Log("[SCHEDULER] Stop time reached. Attempting to cancel REGULAR recording.");
                        recFuture.cancel(true);
                        LogHelper.Log("[SCHEDULER] REGULAR recording cancelled (or already completed).");
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
                        LogHelper.LogWarning("[SCHEDULER] Failed to start REGULAR recording (attempt " + retryCount + "/" + recRetries + ") for channel: " + displayName + ". Retrying in " + (recRetriesDelay/1000) + " seconds. Error: " + LogHelper.printStackTrace(e));
                        try { Thread.sleep(recRetriesDelay); } catch (Exception t) { LogHelper.LogError("[SCHEDULER] Error while waiting between REGULAR attempts: " + LogHelper.printStackTrace(t)); }
                    } else {
                        LogHelper.LogError("[SCHEDULER] Could not start REGULAR recording after " + recRetries + " attempts for channel: " + displayName + ". Error: " + LogHelper.printStackTrace(e));
                        // Try to delete the created folder if empty
                        try {
                            String channelNameToDelete = sanitizedChannel != null ? sanitizedChannel : groupTitle;
                            java.io.File channelDir = new java.io.File(outputPath, channelNameToDelete);
                            if (channelDir.exists() && channelDir.isDirectory() && channelDir.list().length == 0) {
                                if (channelDir.delete()) {
                                    LogHelper.Log("[SCHEDULER] Deleted empty recording folder: " + channelDir.getAbsolutePath());
                                } else {
                                    LogHelper.LogWarning("[SCHEDULER] Could not delete empty recording folder: " + channelDir.getAbsolutePath());
                                }
                            }
                        } catch (Exception delEx) {
                            LogHelper.LogWarning("[SCHEDULER] Exception while trying to delete empty recording folder: " + LogHelper.printStackTrace(delEx));
                        }
                        LogHelper.Log("[SCHEDULER] Process ended since no recording could be done!");
                        LogHelper.LogError("[SCHEDULER] Process exiting with exit(1)");
                        shutdownAndExit(1);
                    }
                }
            }
        }
        // Start a failsafe timer thread to exit at stop time
        Thread stopTimer = new Thread(() -> {
            try {
                // Use already declared formatter and stop
                LocalTime stopLocal = LocalTime.parse(stopTime, formatter);
                ZonedDateTime stopDateTime = ZonedDateTime.now(zone);
                if (stopDateTime.isBefore(stopDateTime.withHour(stopLocal.getHour()).withMinute(stopLocal.getMinute()).withSecond(0).withNano(0))) {
                    stopDateTime = stopDateTime.plusDays(1);
                }
                while (true) {
                    ZonedDateTime current = ZonedDateTime.now(zone);
                    
                    if (!current.isBefore(stopDateTime)) {
                        LogHelper.Log("[SCHEDULER] Failsafe timer reached stop time. Exiting with System.exit(0)");
                        shutdownAndExit(0);
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                LogHelper.LogError("[SCHEDULER] Failsafe timer error: " + LogHelper.printStackTrace(e));
                LogHelper.LogError("[SCHEDULER] Failsafe timer exiting with System.exit(1)");
                shutdownAndExit(1);
            }
        });
        stopTimer.setDaemon(true);
        stopTimer.start();
    }

    // Create and configure RecorderHelper for REGULAR-mode
    private static RecorderHelper createConfiguredHelperReg(String url, String startTime, String stopTime, M3UHolder channelInfo, String logConfigPath, String timezone, boolean is24Hour, String logFile, String groupTitle, String tvgId, int recRetries, int recRetriesDelay, String tvgLogo, String tvgName) {
        RecorderHelper helperReg = new RecorderHelper(new UserInputHelper(new java.util.Scanner(System.in), System.out));
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
            java.util.concurrent.ExecutorService exec = se.eskimos.recorder.RecorderHelper.getExecutor();
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