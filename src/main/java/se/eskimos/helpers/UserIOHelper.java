package se.eskimos.helpers;
import java.io.PrintStream;
import java.util.Scanner;
import se.eskimos.log.LogHelper;
import se.eskimos.m3u.M3UHolder;

/**
 * Helper class for user input and output. Allows injection for testability.
 */
public class UserIOHelper {
    private final Scanner scanner;
    private final PrintStream out;

    public UserIOHelper(Scanner scanner, PrintStream out) {
        this.scanner = scanner;
        this.out = out;
    }

    /**
     * Prompts the user and reads a line of input.
     * @param prompt The prompt to display
     * @return The trimmed user input, or null if input is not available
     */
    public String promptAndRead(String prompt) {
        out.println(prompt);
        try {
            String input = scanner.nextLine();
            return input == null ? null : input.trim();
        } catch (Exception e) {
            LogHelper.LogError(se.eskimos.helpers.TextHelper.EXCEPTION_IN_USERIOHELPER_READINPUT, e);
            return null;
        }
    }

    /**
     * Prints a message to the user.
     * @param message The message to print
     */
    public void print(String message) {
        out.println(message);
    }

    /**
     * Prints a channel list with dynamic alignment for channel code, using a fixed maxNameLength for all pages.
     */
    public void printChannelList(java.util.List<M3UHolder> channels, int startIndex, int endIndex, int maxNameLength) {
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
            print(line);
            print(StringAndFileHelper.createUnderLine(line.length()));
        }
    }

    /**
     * Displays up to 20 search results using UserIOHelper, with dynamic alignment for channel code
     */
    public void displaySearchResults(java.util.List<M3UHolder> matches, int maxNameLength) {
        printChannelList(matches, 0, Math.min(matches.size(), StringAndFileHelper.CHANNELS_PER_PAGE), maxNameLength);
    }

    /**
     * Displays recording status information
     */
    public void displayRecordingStatus(String text, boolean isStart, String timeFrom, String timeTo, String url) {
        clearScreen();
        String displayTime = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        se.eskimos.log.LogHelper.Log("\n" + se.eskimos.helpers.TextHelper.RECORDING_STARTIME + timeFrom.trim());
        se.eskimos.log.LogHelper.Log(se.eskimos.helpers.TextHelper.RECORDING_STOPTIME + timeTo.trim());
        se.eskimos.log.LogHelper.Log(se.eskimos.helpers.TextHelper.URL_OF_CHANNEL + url.trim() + "\n");
        if (isStart) {
            se.eskimos.log.LogHelper.LogWarning("\n" + text + displayTime);
        } else {
            se.eskimos.log.LogHelper.Log(text + displayTime);
        }
    }

    /**
     * Clears the console screen based on operating system
     */
    public void clearScreen() {
        try {
            String os = System.getProperty("os.name");
            if (os.contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // Ignore errors, fallback to printing newlines
            for (int i = 0; i < 50; i++) System.out.println();
        }
    }
} 