package se.eskimos.recorder;

/**
 * Centralized help, error, and user-facing texts for the IPTV Recorder CLI.
 */
public class HelpText {
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
} 