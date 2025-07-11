package se.eskimos.recorder;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.File;

/**
 * Helper class for building filenames for recordings.
 */
public class FileNameBuilder {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String FILE_EXTENSION = ".ts";
    private static final String PLUS_REPLACEMENT = "plus";

    /**
     * Creates a filename for the recording based on channel information and timing.
     * @param filePath Base path for the recording
     * @param zone Timezone for date formatting
     * @param channel Channel information
     * @param timeFrom Start time (HH:mm)
     * @param timeTo Stop time (HH:mm)
     * @return Full path to the recording file
     */
    public static String createFileName(String filePath, ZoneId zone, M3UHolder channel, String timeFrom, String timeTo) {
        String date = DATE_FORMATTER.withZone(zone).format(java.time.ZonedDateTime.now(zone));
        String tvgName = channel != null && channel.tvgName() != null && !channel.tvgName().isEmpty() ? channel.tvgName().trim() : null;
        String groupTitle = channel != null && channel.groupTitle() != null ? channel.groupTitle().trim() : "";

        String folder;
        String fileNameBase;
        String group = RecorderHelper.sanitizeForFileName(groupTitle);
        String start = (timeFrom != null) ? timeFrom.replace(":", "") : "";
        String stop = (timeTo != null) ? timeTo.replace(":", "") : "";
        String sportPart = "";

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
            folder = RecorderHelper.sanitizeForFileName(tvgName);
            fileNameBase = folder + "_" + date + "_" + start + "_" + stop;
        } else {
            folder = group;
            fileNameBase = group + "_" + date + "_" + start + "_" + stop;
        }

        if (fileNameBase.length() > MAX_FILENAME_LENGTH - FILE_EXTENSION.length()) {
            fileNameBase = fileNameBase.substring(0, MAX_FILENAME_LENGTH - FILE_EXTENSION.length());
        }

        File eventDir = new File(filePath, folder);
        if (!eventDir.exists()) eventDir.mkdirs();
        String fileName = fileNameBase + FILE_EXTENSION;
        fileName = fileName.replace("+", PLUS_REPLACEMENT);
        return new File(eventDir, fileName).getAbsolutePath();
    }
} 