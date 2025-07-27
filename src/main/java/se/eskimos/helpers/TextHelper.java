package se.eskimos.helpers;

/**
 * Centralized help, error, and user-facing texts for the IPTV Recorder CLI.
 */
public class TextHelper {
    public static final String MAIN_HELP =
        "Usage: java -jar IPTV-Recorder.jar [--help] [config.properties]\n" +
        "You can specify the config file as the first argument, or use --config <path>.\n" +
        "All configuration is in config.properties or via environment variables.\n" +
        "See README.md for details.";

    public static final String EXIT_CHANNEL_SELECTION = "Exiting channel selection.";
    public static final String BACKGROUND_PROCESS_STARTED = "A background process has started, check log file for more logging.";
    public static final String ERROR_PREFIX = "Error: ";
    public static final String MISSING_URL = "Missing required config: url";
    public static final String MISSING_M3UFILE = "Missing required config: m3uFile";
    public static final String PROMPT_CHANNEL_SELECTION = "\nPlease choose a channel code you want to record from the list, press ENTER to see next page, type a search term to filter, or type 'q' to quit: ";
    public static final String PROMPT_SEARCH_RESULTS = "\nEnter channel code to select, press ENTER to return to normal paging, or type a new search term:";
    public static final String NO_CHANNELS_FOUND = "\nNo channels found matching: '%s'. Press ENTER to return to the main list or enter a new search term:";
    public static final String CHANNEL_CODE_NOT_FOUND = "Channel code not found in search results. Try again, or press ENTER to return to the main list.";
    public static final String SEARCH_RESULTS_HEADER = "\nSearch results for: '%s' (showing up to 20 results):";
    public static final String INVALID_CHANNEL_SELECTION = "\nThe channel that you have entered does not exist, reloading channel list";
    public static final String START_TIME_ALREADY_SET = "Start time was already set to '%s'. Enter end time:";
    public static final String PRESS_ENTER_TO_CONTINUE = "Press Enter to continue...";
    public static final String WRONG_TIME_FORMAT = "You have entered the wrong time format, it should be HH:MM, please try again: \n";
    public static final String DOWNLOAD_M3U_PROGRESS = "Download of M3U file in progress..";
    public static final String DOWNLOADING = "Downloading...";
    public static final String DOWNLOADING_PERCENT = "\rDownloading: %d%%";
    public static final String DOWNLOADING_DONE = "\rDownloading: 100%         ";
    public static final String URL_CANNOT_BE_NULL = "URL cannot be null or empty";
    public static final String INVALID_URL_FORMAT = "Invalid URL format: %s";
    public static final String INSUFFICIENT_DISK_SPACE = "Insufficient disk space. Available: %dMB, Required: %dMB";
    public static final String MALFORMED_URL = "Malformed URL: %s";
    public static final String IO_ERROR_DURING_RECORDING = "IO error during regular recording: %s";
    public static final String USER_INPUT_NULL = "User input was null (possibly EOF or cancelled)";
    public static final String USER_INPUT_NULL_SEARCH = "User input was null during search (possibly EOF or cancelled)";
    public static final String USER_INPUT_NULL_SEARCH_RESULT = "User input was null during search result input";
    public static final String USER_INPUT_NULL_CHANNEL_CODE = "User input was null during channel code retry";
    public static final String USER_CANCELLED = "User input was null";
    public static final String USER_CHOSE_QUIT = "User chose to quit";
    public static final String USER_CHOSE_QUIT_SEARCH = "User chose to quit from search";
    public static final String USER_CHOSE_QUIT_SEARCH_RETRY = "User chose to quit from search retry";
    public static final String USER_CHOSE_QUIT_CHANNEL_CODE_RETRY = "User chose to quit from channel code retry";
    public static final String START_TIME_EXTRACTED = "Start time '%s' was automatically extracted from the channel name.";
    public static final String WAITING_FOR_RECORDING_START = "Waiting for recording to start ... ";
    public static final String START_COUNTER_INTERRUPTED = "Start counter was interrupted";
    public static final String RECORDING_STARTED_WAITING_FOR_END = "Recording has started, waiting for recording to end ... ";
    public static final String END_COUNTER_INTERRUPTED = "End counter was interrupted";
    public static final String RECORDING_STARTIME = "\nRecording startime: ";
    public static final String RECORDING_STOPTIME = "Recording stoptime: ";
    public static final String URL_OF_CHANNEL = "URL of channel:     ";
    public static final String FFMPEG_OUTPUT_THREAD_ERROR = "[FFMPEG] Output thread error: ";
    public static final String IO_ERROR_DURING_REGULAR_RECORDING = "IO error during regular recording: ";
    public static final String STOP_NO_URL_SET = "[STOP] No URL set, no process to kill.";
    public static final String STOP_REGULAR_MODE_NO_SEPARATE_PROCESS = "[STOP] Regular mode: no separate process to kill, only file stream will be closed.";
    public static final String STOPPED_FFMPEG_PROCESS_AND_ENDED_PROGRAM = "[STOP] Stopped ffmpeg process and ended the program";
    public static final String NO_FFMPEG_PROCESS_TO_KILL_OR_ALREADY_TERMINATED = "[STOP] No ffmpeg process to kill or already terminated.";
    public static final String CHANNEL_NAME_PREFIX = "Channel name: ";
    public static final String CHANNEL_CODE_PREFIX = "Channel code: ";
    public static final String SPECIAL_SCENARIO_TOO_FEW_ARGS = "Too few arguments for special scenario. Usage: config.properties 'search string' 'start time' 'stop time'";
    public static final String SPECIAL_SCENARIO_NO_MATCH = "No channel matched the search: '%s'";
    public static final String SPECIAL_SCENARIO_START_TIME_EXTRACTED = "Start time '%s' was automatically extracted from the channel name.";
    public static final String REGULAR_INPUTSTREAM_ENDED = "[REGULAR] InputStream ended before stop time. Stream may have been dropped or closed by server.";
    public static final String SCHEDULER_LOGGING_STARTED = "[SCHEDULER] Logging started. Log file: %s";
    public static final String SCHEDULER_LOGGING_ONLY_TERMINAL = "[SCHEDULER] Logging only to terminal. No log file specified.";
    public static final String SCHEDULER_START_TIME_PASSED = "[SCHEDULER] Start time '%s' has already passed, starting immediately.";
    public static final String SCHEDULER_WAITING_UNTIL_START = "[SCHEDULER] Waiting %d seconds until start time %s";
    public static final String SCHEDULER_ATTEMPTING_START = "[SCHEDULER] Attempting to start recording (attempt %d/%d) for channel: %s";
    public static final String SCHEDULER_FAILED_START_FFMPEG = "[SCHEDULER] Failed to start FFMPEG recording (attempt %d/%d) for channel: %s. Retrying in %d seconds. Error: %s";
    public static final String SCHEDULER_COULD_NOT_START_FFMPEG = "[SCHEDULER] Could not start FFMPEG recording after %d attempts for channel: %s. Error: %s";
    public static final String SCHEDULER_RECORDING_IN_PROGRESS = "[SCHEDULER] Recording in progress, waiting until stop time %s...";
    public static final String SCHEDULER_STOP_TIME_REACHED = "[SCHEDULER] Stop time reached. Stopping recording.";
    public static final String SCHEDULER_STARTED_RECORDING_FOR_CHANNEL = "[SCHEDULER] Started recording for channel: %s";
    public static final String SCHEDULER_RECORDING_STARTED = "[SCHEDULER] Started recording for channel: %s from %s to %s";
    public static final String SCHEDULER_STOP_TIME_REACHED_CANCEL_REGULAR = "[SCHEDULER] Stop time reached. Attempting to cancel REGULAR recording.";
    public static final String SCHEDULER_REGULAR_RECORDING_CANCELLED = "[SCHEDULER] REGULAR recording cancelled (or already completed).";
    public static final String SCHEDULER_DELETED_EMPTY_RECORDING_FOLDER = "[SCHEDULER] Deleted empty recording folder: %s";
    public static final String SCHEDULER_COULD_NOT_DELETE_EMPTY_RECORDING_FOLDER = "[SCHEDULER] Could not delete empty recording folder: %s";
    public static final String SCHEDULER_EXCEPTION_DELETING_EMPTY_FOLDER = "[SCHEDULER] Exception while trying to delete empty recording folder: %s";
    public static final String SCHEDULER_PROCESS_ENDED_NO_RECORDING = "[SCHEDULER] Process ended since no recording could be done!";
    public static final String SCHEDULER_FAILSAFE_TIMER_REACHED_STOP_TIME = "[SCHEDULER] Failsafe timer reached stop time. Exiting with System.exit(0)";
    public static final String SCHEDULER_FAILSAFE_TIMER_ERROR = "[SCHEDULER] Failsafe timer error: ";
    public static final String SCHEDULER_FAILSAFE_TIMER_EXITING = "[SCHEDULER] Failsafe timer exiting with System.exit(1)";
    public static final String SCHEDULER_EXECUTOR_TIMEOUT = "[SCHEDULER] Executor did not terminate within timeout, forcing shutdownNow.";
    public static final String SCHEDULER_ERROR_EXECUTOR_SHUTDOWN = "[SCHEDULER] Error during executor shutdown: %s";
    public static final String SCHEDULER_UNCAUGHT_THROWABLE = "[SCHEDULER] Uncaught throwable in thread %s: %s";
    public static final String SCHEDULER_PROCESS_EXITING = "[SCHEDULER] Process exiting with exit(1)";
    public static final String REGULAR_EXCEPTION_DURING_RECORDING = "[REGULAR] Exception during recording: %s";

    public static final String REGULAR_DOWNLOADED_TVG_LOGO = "Downloaded tvg-logo for channel: %s";
    public static final String REGULAR_FAILED_TO_DOWNLOAD_TVG_LOGO = "Failed to download tvg-logo for channel: %s. %s";
    public static final String REGULAR_NO_TVG_LOGO_FOUND = "No tvg-logo found for channel: %s";
    public static final String SCHEDULER_ERROR_WAITING_BETWEEN_ATTEMPTS = "[SCHEDULER] Error while waiting between attempts: ";
    public static final String SCHEDULER_ELLIPSIS = "...";
    public static final String SCHEDULER_ERROR_DURING_STOP = "[SCHEDULER] Error during recording stop: ";
    public static final String SCHEDULER_RECORDING_COULD_NOT_BE_STARTED = "[SCHEDULER] Recording could not be started. Process exiting with exit(1)";
    public static final String SCHEDULER_STARTED_RECORDING = "[SCHEDULER] Started recording for channel: %s";
    public static final String SCHEDULER_STOP_TIME_REACHED_ATTEMPTING_CANCEL = "[SCHEDULER] Stop time reached. Attempting to cancel REGULAR recording.";
    public static final String SCHEDULER_FAILED_START_REGULAR = "[SCHEDULER] Failed to start REGULAR recording (attempt %d/%d) for channel: %s. Retrying in %d seconds. Error: %s";
    public static final String SCHEDULER_COULD_NOT_START_REGULAR = "[SCHEDULER] Could not start REGULAR recording after %d attempts for channel: %s. Error: %s";
    public static final String SCHEDULER_EXCEPTION_DELETING_EMPTY_RECORDING_FOLDER = "[SCHEDULER] Exception while trying to delete empty recording folder: ";
    public static final String FAILED_TO_LOAD_PROPERTIES_FILE = "Failed to load properties file: ";
    public static final String IO_ERROR_LOADING_PROPERTIES_FILE = "IO error while loading properties file: ";
    public static final String EXCEPTION_IN_RECORDERHELPER_GETM3UFILE = "Exception in RecorderHelper.getM3UFile";
    public static final String EXCEPTION_IN_READ_INPUT = "Exception in RecorderHelper.readInput";
    public static final String NO_INPUT_AVAILABLE = "[INPUT] No input available (NoSuchElementException): ";
    public static final String SCANNER_CLOSED = "[INPUT] Scanner is closed (IllegalStateException): ";
    public static final String UNKNOWN_ERROR_WHILE_READING_INPUT = "[INPUT] Unknown error while reading input: ";
    public static final String APPLICATION_ERROR = "Application error: %s";
    public static final String MISSING_DESTINATION_PATH = "Missing required config: destinationPath";
    public static final String FAILED_TO_LOAD_CONFIG = "Failed to load config: ";
    public static final String INVALID_TIMEZONE_FORMAT = "Invalid timezone format: %s, using default: %s";
    public static final String EXCEPTION_IN_USERIOHELPER_READINPUT = "Exception in UserIOHelper.readInput";
    public static final String FAILED_TO_SEND_EXCEPTION_MAIL = "Failed to send exception mail: ";
    public static final String M3U_FILE_NOT_FOUND = "M3U file not found: ";
    public static final String FAILED_TO_PARSE_M3U_FILE = "Failed to parse M3U file: ";
} 