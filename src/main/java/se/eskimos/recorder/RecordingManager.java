package se.eskimos.recorder;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import se.eskimos.helpers.DateTimeHelper;

/**
 * Handles starting and stopping of recordings (FFMPEG and REGULAR).
 */
public class RecordingManager {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private Process ffmpegProcess;

    /**
     * Starts an FFMPEG recording process.
     * @param url Stream URL
     * @param outputFile Output file path
     * @throws IOException if process cannot be started
     */
    public void startRecFFMPEG(String url, String outputFile) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-hide_banner", "-loglevel", "panic",
            "-i", url, "-c", "copy", outputFile
        );
        pb.redirectErrorStream(true);
        this.ffmpegProcess = pb.start();
    }

    /**
     * Starts a regular recording using input/output streams until stopTime.
     * @param url Stream URL
     * @param outputFile Output file path
     * @param stopTime Stop time (HH:mm)
     * @throws IOException if recording cannot be started
     */
    public void startRecRegular(String url, String outputFile, String stopTime) throws IOException {
        LocalTime targetTime = DateTimeHelper.parseFlexibleLocalTime(stopTime, TIME_FORMATTER);
        try (var input = java.net.URI.create(url).toURL().openStream();
             var outputStream = new java.io.FileOutputStream(new java.io.File(outputFile))) {
            byte[] bytes = new byte[8192];
            int read;
            while ((read = input.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
                LocalTime currentTime = LocalTime.now();
                if (currentTime.isAfter(targetTime) || currentTime.equals(targetTime)) {
                    break;
                }
            }
        }
    }

    /**
     * Stops the current FFMPEG recording process if running.
     */
    public void stopFFMPEG() {
        if (this.ffmpegProcess != null && this.ffmpegProcess.isAlive()) {
            this.ffmpegProcess.destroy();
        }
    }
} 