package se.eskimos.recorder;

import java.util.List;
import java.util.function.Consumer;

/**
 * Utility class for string operations
 */
public class StringHelper {
    
    public static final int CHANNELS_PER_PAGE = 20;

    /**
     * Creates a string of spaces with the specified length
     * @param spaces Number of spaces to create
     * @return String containing the specified number of spaces, or empty string if length is <= 0
     */
    public static String createSpaces(int spaces) {
        if (spaces <= 0) return "";
        return " ".repeat(spaces);
    }

    /**
     * Creates a string of underscores with the specified length
     * @param lines Number of underscores to create
     * @return String containing the specified number of underscores, or empty string if length is <= 0
     */
    public static String createUnderLine(int lines) {
        if (lines <= 0) return "";
        return "_".repeat(lines);
    }

    /**
     * Calculates the maximum length of the channel name (tvgName or name) in the given list.
     * @param channels List of channels
     * @return Maximum length of channel name
     */
    public static int getMaxChannelNameLength(List<M3UHolder> channels) {
        int max = 0;
        for (M3UHolder mH : channels) {
            String name = mH.tvgName() != null && !mH.tvgName().isEmpty() ? mH.tvgName().trim() : mH.name().trim();
            if (name.length() > max) max = name.length();
        }
        return max;
    }

    /**
     * Prints a channel list with dynamic alignment for channel code, using a fixed maxNameLength for all pages.
     * @param channels List of channels
     * @param startIndex Start index (inclusive)
     * @param endIndex End index (exclusive)
     * @param printLine Function to print a line (e.g. userIO::print or out::println)
     * @param maxNameLength The maximum channel name length to use for alignment
     */
    public static void printChannelList(List<M3UHolder> channels, int startIndex, int endIndex, Consumer<String> printLine, int maxNameLength) {
        String nameLabel = "Channel name: ";
        String codeLabel = "Channel code: ";
        int codeCol = nameLabel.length() + maxNameLength + 4; // 4 spaces after name
        
        for (int i = startIndex; i < endIndex; i++) {
            M3UHolder mH = channels.get(i);
            String name = mH.tvgName() != null && !mH.tvgName().isEmpty() ? mH.tvgName().trim() : mH.name().trim();
            String code = mH.code().trim();
            StringBuilder sb = new StringBuilder();
            sb.append(nameLabel);
            sb.append(String.format("%-" + maxNameLength + "s", name)); // left-align name
            sb.append(" ".repeat(4));
            int spaces = codeCol - (nameLabel.length() + name.length());
            if (spaces < 1) spaces = 1;
            sb.append(codeLabel);
            sb.append(code);
            String line = sb.toString();
            printLine.accept(line);
            printLine.accept(createUnderLine(line.length()));
        }
    }
} 