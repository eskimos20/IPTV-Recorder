package se.eskimos.recorder;

/**
 * Utility class for string operations
 */
public class StringHelper {
    
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
} 