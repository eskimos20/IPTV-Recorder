package se.eskimos.helpers;

import java.util.List;
import java.time.format.DateTimeFormatter;
import java.io.File;
import se.eskimos.m3u.M3UHolder;

/**
 * Utility class for string operations and file name building
 */
public class StringAndFileHelper {
    public static final int CHANNELS_PER_PAGE = 20;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String FILE_EXTENSION = ".ts";
    private static final String PLUS_REPLACEMENT = "plus";

    public static String createSpaces(int spaces) {
        if (spaces <= 0) return "";
        return " ".repeat(spaces);
    }

    public static String createUnderLine(int lines) {
        if (lines <= 0) return "";
        return "_".repeat(lines);
    }

    public static int getMaxChannelNameLength(List<M3UHolder> channels) {
        int max = 0;
        for (M3UHolder mH : channels) {
            String name = mH.tvgName() != null && !mH.tvgName().isEmpty() ? mH.tvgName().trim() : mH.name().trim();
            if (name.length() > max) max = name.length();
        }
        return max;
    }

    /**
     * Sanitizes a string for use in file/folder names by replacing special characters with underscores
     * @param input The string to sanitize
     * @return Sanitized string safe for file/folder names
     */
    public static String sanitizeForFileName(String input) {
        if (input == null || input.isEmpty()) {
            return "Unknown";
        }
        return input.replaceAll("[\\s]+", "_")
                    .replaceAll("[^a-zA-Z0-9_]", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_+|_+$", "");
    }

    /**
     * Creates a filename for the recording based on channel information and timing, with subfolder for date/start/stop.
     * Use this for per-recording subfolders.
     */
    public static String createFileNameWithSubfolder(String filePath, java.time.ZoneId zone, se.eskimos.m3u.M3UHolder channel, String timeFrom, String timeTo) {
        String date = DATE_FORMATTER.withZone(zone).format(java.time.ZonedDateTime.now(zone)).replace("-", "_");
        String tvgName = channel != null && channel.tvgName() != null && !channel.tvgName().isEmpty() ? channel.tvgName().trim() : null;
        String groupTitle = channel != null && channel.groupTitle() != null ? channel.groupTitle().trim() : "";
        String folder;
        String fileNameBase;
        String group = sanitizeForFileName(groupTitle);
        String start = (timeFrom != null) ? timeFrom.replace(":", "") : "";
        String stop = (timeTo != null) ? timeTo.replace(":", "") : "";
        String sportPart = "";
        String secondFolder = date + "_" + start + "_" + stop;
        if (channel != null && (channel.tvgId() == null || channel.tvgId().isEmpty())) {
            folder = group;
            if (tvgName != null && !tvgName.isEmpty()) {
                String[] matches = SportsEventsHelper.extractAllEventsAndStages(tvgName);
                if (matches.length > 0) {
                    sportPart = "_" + String.join("_", matches);
                }
            }
            fileNameBase = group + sportPart + "_" + date + "_" + start + "_" + stop;
        } else if (tvgName != null && !tvgName.isEmpty()) {
            folder = sanitizeForFileName(tvgName);
            fileNameBase = folder + "_" + date + "_" + start + "_" + stop;
        } else {
            folder = group;
            fileNameBase = group + "_" + date + "_" + start + "_" + stop;
        }
        if (fileNameBase.length() > MAX_FILENAME_LENGTH - FILE_EXTENSION.length()) {
            fileNameBase = fileNameBase.substring(0, MAX_FILENAME_LENGTH - FILE_EXTENSION.length());
        }
        File eventDir = new File(filePath, folder + File.separator + secondFolder);
        if (!eventDir.exists()) eventDir.mkdirs();
        String fileName = fileNameBase + FILE_EXTENSION;
        fileName = fileName.replace("+", PLUS_REPLACEMENT);
        return new File(eventDir, fileName).getAbsolutePath();
    }
} 