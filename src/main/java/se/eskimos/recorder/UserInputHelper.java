package se.eskimos.recorder;
import java.io.PrintStream;
import java.util.Scanner;
import se.eskimos.log.LogHelper;

/**
 * Helper class for user input and output. Allows injection for testability.
 */
public class UserInputHelper {
    private final Scanner scanner;
    private final PrintStream out;

    public UserInputHelper(Scanner scanner, PrintStream out) {
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
            LogHelper.LogError("Exception in UserInputHelper.readInput", e);
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
} 