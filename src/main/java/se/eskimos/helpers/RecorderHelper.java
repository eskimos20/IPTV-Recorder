package se.eskimos.helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import se.eskimos.log.LogHelper;
import java.util.ArrayList;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import se.eskimos.m3u.M3UHolder;

public class RecorderHelper {
	
	// Constants for magic numbers
	private static final int BUFFER_SIZE = 8192;
	private static final int SLEEP_INTERVAL_MS = 1000;
	private static final long MIN_DISK_SPACE_BYTES = 1024 * 1024 * 100; // 100MB minimum
	
	private String timeFrom = "";
	private String timeTo = "";
	private String url = "";
	private String errorMessage = "";
	
	// Add recording mode tracking
	private RecordingMode recordingMode = RecordingMode.REGULAR;
	
	private static final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
	static {
		// Add shutdown hook for resource cleanup
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				executor.shutdown();
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				executor.shutdownNow();
			}
		}));
	}
	
	// Enum for recording mode
	private enum RecordingMode {
		FFMPEG, REGULAR
	}
	
	// Static formatters for efficient time handling
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
	
	/**
	 * Validates URL format and disk space availability
	 * @param url The URL to validate
	 * @param destinationPath The destination path to check disk space
	 * @throws IllegalArgumentException if validation fails
	 */
	private void validateRecordingSetup(String url, String destinationPath) {
		// Validate URL format
		if (url == null || url.trim().isEmpty()) {
			throw new IllegalArgumentException(TextHelper.URL_CANNOT_BE_NULL);
		}
		
		try {
			new java.net.URI(url);
		} catch (java.net.URISyntaxException e) {
			throw new IllegalArgumentException(String.format(TextHelper.INVALID_URL_FORMAT, url));
		}
		
		// Check disk space
		File destinationDir = new File(destinationPath);
		if (!destinationDir.exists()) {
			destinationDir.mkdirs();
		}
		
		long freeSpace = destinationDir.getFreeSpace();
		if (freeSpace < MIN_DISK_SPACE_BYTES) {
			throw new IllegalArgumentException(String.format(TextHelper.INSUFFICIENT_DISK_SPACE, (freeSpace / (1024 * 1024)), (MIN_DISK_SPACE_BYTES / (1024 * 1024))));
		}
	}
	
	// Add a field to store the max channel name length for the session
	private int maxNameLength = -1;

	/**
	 * Displays channel list with pagination and returns a status string.
	 * @param myChannels List of channels to display
	 * @param restarted Whether this is a restart
	 * @param text Additional text to display
	 * @param destinationPath Destination path for recordings
	 * @param startIndex Index to start displaying channels from
	 * @return "CHANNEL_SELECTED" if a channel was selected, "NEXT_PAGE" if user pressed Enter, "QUIT" if user wants to quit
	 */
	public ChannelSelectionResult loadChannels(ArrayList<M3UHolder> myChannels, boolean restarted, String text, String destinationPath, int startIndex) {
        // Only clear screen at the start of paging, not after a failed search
        userIO.clearScreen();
        int totalChannels = myChannels.size();
        int endIndex = Math.min(startIndex + StringAndFileHelper.CHANNELS_PER_PAGE, totalChannels);
        // Calculate max channel name length for the entire list (once per session)
        if (maxNameLength < 0) {
            maxNameLength = StringAndFileHelper.getMaxChannelNameLength(myChannels);
        }
        // Use the same method for displaying channels as in search, with logging and alignment
        userIO.printChannelList(myChannels, startIndex, endIndex, maxNameLength);
        // If there are more channels, ask for input
        if (endIndex < totalChannels) {
            ChannelSelectionResult result = waitForChannelInput(myChannels, restarted, text, destinationPath, startIndex);
            if (result == ChannelSelectionResult.RESTART_MAIN_PROMPT) {
                userIO.clearScreen(); // Ensure list is shown again after failed search
                return loadChannels(myChannels, restarted, text, destinationPath, startIndex);
            }
            return result;
        } else {
            // Last page, ask for input but don't increment page further
            ChannelSelectionResult result = waitForChannelInput(myChannels, restarted, text, destinationPath, startIndex);
            if (result == ChannelSelectionResult.RESTART_MAIN_PROMPT) {
                userIO.clearScreen(); // Ensure list is shown again after failed search
                return loadChannels(myChannels, restarted, text, destinationPath, startIndex);
            }
            return result;
        }
    }

    /**
     * Enum for channel selection result/state.
     */
    public enum ChannelSelectionResult {
        CHANNEL_SELECTED,
        NEXT_PAGE,
        QUIT,
        RESTART_MAIN_PROMPT
    }

    /**
     * Exception for user cancellation or quit.
     */
    public static class UserCancelledException extends RuntimeException {
        public UserCancelledException(String msg) { super(msg); }
    }

    private final UserIOHelper userIO;
    
    /**
     * Constructs a RecorderHelper with a given UserInputHelper and HelpText for testable input/output and messages.
     */
    public RecorderHelper(UserIOHelper userIO) {
        this.userIO = userIO;
    }

    /**
     * Waits for user input to select a channel (with paging support), using injected UserInputHelper for I/O.
     * @param myChannels List of available channels
     * @param restarted Whether this is a restart
     * @param text Additional text to display
     * @param destinationPath Destination path for recordings
     * @param startIndex Index of first channel on this page
     * @return ChannelSelectionResult (enum)
     */
    public ChannelSelectionResult waitForChannelInput(ArrayList<M3UHolder> myChannels, boolean restarted, String text, String destinationPath, int startIndex) {
        if (!getErrorMessage().isEmpty()) {
            LogHelper.LogError(getErrorMessage());
            setErrorMessage("");
        }
        boolean done = false;
        boolean forcePrompt = false;
        String input = "";
        while (true) {
            if (!forcePrompt) {
                input = userIO.promptAndRead(TextHelper.PROMPT_CHANNEL_SELECTION);
            } else {
                forcePrompt = false;
            }
            if (input == null) {
                LogHelper.LogError(TextHelper.USER_INPUT_NULL);
                throw new UserCancelledException(TextHelper.USER_INPUT_NULL);
            }
            if (input.isEmpty()) {
                return ChannelSelectionResult.NEXT_PAGE;
            }
            if (input.equalsIgnoreCase("q")) {
                LogHelper.Log(TextHelper.USER_CHOSE_QUIT);
                throw new UserCancelledException(TextHelper.USER_CHOSE_QUIT);
            }
            if (isNumeric(input)) {
                boolean selected = handleChannelCodeSelection(myChannels, input, destinationPath);
                if (selected) {
                    break;
                }
            } else {
                ChannelSelectionResult searchResult = searchChannelsLoop(myChannels, input, destinationPath);
                if (searchResult == ChannelSelectionResult.CHANNEL_SELECTED) {
                    return ChannelSelectionResult.CHANNEL_SELECTED;
                } else if (searchResult == ChannelSelectionResult.RESTART_MAIN_PROMPT) {
                    return ChannelSelectionResult.RESTART_MAIN_PROMPT;
                } else if (searchResult == ChannelSelectionResult.QUIT) {
                    LogHelper.Log(TextHelper.USER_CHOSE_QUIT_SEARCH);
                    throw new UserCancelledException(TextHelper.USER_CHOSE_QUIT_SEARCH);
                } else if (searchResult == ChannelSelectionResult.NEXT_PAGE) {
                    return ChannelSelectionResult.NEXT_PAGE;
                }
            }
            if (done) {
                break;
            } else {
                setErrorMessage(TextHelper.INVALID_CHANNEL_SELECTION);
                LogHelper.LogError(TextHelper.INVALID_CHANNEL_SELECTION);
                return ChannelSelectionResult.NEXT_PAGE;
            }
        }
        return ChannelSelectionResult.CHANNEL_SELECTED;
    }

    private boolean handleChannelCodeSelection(ArrayList<M3UHolder> myChannels, String input, String destinationPath) {
        for (M3UHolder mH : myChannels) {
            if (mH.code().equalsIgnoreCase(input)) {
                this.url = mH.url();
                String nameForTime = (mH.tvgName() != null && !mH.tvgName().isEmpty() ? mH.tvgName() : mH.name());
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2})").matcher(nameForTime);
                if (matcher.find()) {
                    this.timeFrom = matcher.group(1);
                    LogHelper.Log(String.format(TextHelper.START_TIME_EXTRACTED, this.timeFrom));
                    userIO.print(String.format(TextHelper.START_TIME_EXTRACTED, this.timeFrom));
                } else {
                    this.timeFrom = "";
                }
                // After channel selection, ensure all relevant fields are set from the selected channel
                this.channelInfo = mH;
                this.tvgName = mH.tvgName();
                this.tvgId = mH.tvgId();
                this.groupTitle = mH.groupTitle();
                return true;
            }
        }
				return false;
			}
			
    /**
     * Handles the search loop for channel selection, using ChannelSearchHelper and UserInputHelper.
     */
    private ChannelSelectionResult searchChannelsLoop(ArrayList<M3UHolder> myChannels, String input, String destinationPath) {
        while (true) {
            java.util.List<M3UHolder> matches = ChannelSearchHelper.searchChannels(myChannels, input);
            if (matches.isEmpty()) {
                String retryInput = userIO.promptAndRead(String.format(TextHelper.NO_CHANNELS_FOUND, input));
                if (retryInput == null) {
                    LogHelper.LogError(TextHelper.USER_INPUT_NULL_SEARCH);
                    throw new UserCancelledException(TextHelper.USER_INPUT_NULL_SEARCH);
                }
                if (retryInput.isEmpty()) {
                    // Only now, when user presses ENTER, return to main list (do NOT clear screen)
                    return ChannelSelectionResult.NEXT_PAGE;
                }
                if (retryInput.equalsIgnoreCase("q")) {
                    LogHelper.Log(TextHelper.USER_CHOSE_QUIT_SEARCH_RETRY);
                    throw new UserCancelledException(TextHelper.USER_CHOSE_QUIT_SEARCH_RETRY);
                }
                // Otherwise: new search string, do a new search
                input = retryInput;
                continue;
            } else {
                userIO.displaySearchResults(matches, maxNameLength);
                String searchInput = userIO.promptAndRead(TextHelper.PROMPT_SEARCH_RESULTS);
                if (searchInput == null) {
                    LogHelper.LogError(TextHelper.USER_INPUT_NULL_SEARCH_RESULT);
                    throw new UserCancelledException(TextHelper.USER_INPUT_NULL_SEARCH_RESULT);
                }
                if (searchInput.isEmpty()) {
                    return ChannelSelectionResult.NEXT_PAGE;
                }
                if (searchInput.equalsIgnoreCase("q")) {
                    LogHelper.Log(TextHelper.USER_CHOSE_QUIT_SEARCH);
                    throw new UserCancelledException(TextHelper.USER_CHOSE_QUIT_SEARCH);
                }
                if (isNumeric(searchInput)) {
                    for (M3UHolder mH : matches) {
                        if (mH.code().equalsIgnoreCase(searchInput)) {
                            this.url = mH.url();
                            String nameForTime = (mH.tvgName() != null && !mH.tvgName().isEmpty() ? mH.tvgName() : mH.name());
                            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2})").matcher(nameForTime);
                            if (matcher.find()) {
                                this.timeFrom = matcher.group(1);
                                LogHelper.Log(String.format(TextHelper.START_TIME_EXTRACTED, this.timeFrom));
                                userIO.print(String.format(TextHelper.START_TIME_EXTRACTED, this.timeFrom));
                            } else {
                                this.timeFrom = "";
                            }
                            // After channel selection, ensure all relevant fields are set from the selected channel
                            this.channelInfo = mH;
                            this.tvgName = mH.tvgName();
                            this.tvgId = mH.tvgId();
                            this.groupTitle = mH.groupTitle();
                            return ChannelSelectionResult.CHANNEL_SELECTED;
                        }
                    }
                    // If no matching channel number was found
                    userIO.print(TextHelper.CHANNEL_CODE_NOT_FOUND);
                    continue;
                }
                // Otherwise: new search string, do a new search
                input = searchInput;
            }
        }
    }

    /**
     * Waits for user input for time selection
     * @param message Message to display to user
     * @param startTime Whether this is for start time input
     * @return The entered time string
     */
	public String waitForTimeInput(String message, boolean startTime) {
		
		String input = "";
		
		if (startTime && this.timeFrom != null && !this.timeFrom.isEmpty()) {
			userIO.print(String.format(TextHelper.START_TIME_ALREADY_SET, this.timeFrom));
			userIO.print(TextHelper.PRESS_ENTER_TO_CONTINUE);
			readInput(); // Wait for user to see the message
			return this.timeFrom;
		}
		
		userIO.print(message);
		
		while(true) {
			
			input = readInput();
			
			if (input == null) { // Check for null input
				return "";
			}
			
			if (input.isEmpty()) {
				return "";
			}
			
			if (correctTimeSyntax(input, startTime)) {
				return input;
			}else {
				userIO.print("");
				userIO.print(TextHelper.WRONG_TIME_FORMAT);
			}
		}
	}
	
	/**
	 * Validates time syntax and sets the appropriate time field
	 * @param time Time string to validate
	 * @param startTime Whether this is for start time
	 * @return true if time syntax is valid
	 */
	public boolean correctTimeSyntax(String time, boolean startTime) {
		    try {
		        DateTimeHelper.parseFlexibleLocalTime(time, TIME_FORMATTER);
		        if (startTime) {
		        	this.timeFrom = time;
		        }else {
		        	this.timeTo = time;
		        }
		        return true;
		    } catch (DateTimeParseException | NullPointerException e) {
		    	return false;
		    }
	}
	
	/**
	 * Checks if a string is numeric
	 * @param strNum String to check
	 * @return true if the string represents a number
	 */
	public boolean isNumeric(String strNum) {
	    if (strNum == null) {
	        return false;
	    }
	    try {
	        Double.parseDouble(strNum);
	    } catch (NumberFormatException nfe) {
	        return false;
	    }
	    return true;
	}
	
	/**
	 * Waits until the start time is reached
	 */
	public void startCounter() {
		
		LocalTime targetTime = DateTimeHelper.parseFlexibleLocalTime(this.timeFrom, TIME_FORMATTER);
		 
		while(true) {
			 
			LocalTime currentTime = LocalTime.now();
			if (currentTime.isAfter(targetTime) || currentTime.equals(targetTime)){
				break;
			}
			
			userIO.displayRecordingStatus(TextHelper.WAITING_FOR_RECORDING_START, true, this.timeFrom, this.timeTo, this.url);
 
			try {
				Thread.sleep(SLEEP_INTERVAL_MS);
			} catch (InterruptedException e) {
				LogHelper.LogWarning(TextHelper.START_COUNTER_INTERRUPTED);
			}
		}
	}
	
	/**
	 * Waits until the end time is reached
	 */
	public void endCounter() {
		
		LocalTime targetTime = DateTimeHelper.parseFlexibleLocalTime(this.timeTo, TIME_FORMATTER);
		 
		while(true) {
			 
			LocalTime currentTime = LocalTime.now();
			if (currentTime.isAfter(targetTime) || currentTime.equals(targetTime)){
				break;
			}
			
			userIO.displayRecordingStatus(TextHelper.RECORDING_STARTED_WAITING_FOR_END, false, this.timeFrom, this.timeTo, this.url);
 
			try {
				Thread.sleep(SLEEP_INTERVAL_MS);
			} catch (InterruptedException e) {
				LogHelper.LogWarning(TextHelper.END_COUNTER_INTERRUPTED);
			}
		}
	}
	
	/**
	 * Starts FFMPEG recording process
	 * @param filePath Destination path for the recording
	 * @throws Exception if recording cannot be started
	 */
	public void startRecFFMPEG(String filePath, boolean isResume) throws Exception {
        recordingMode = RecordingMode.FFMPEG;
        validateRecordingSetup(this.url, filePath);
        String outputFile = StringAndFileHelper.createFileNameWithSubfolder(filePath, LogHelper.getTimeZone(), this.channelInfo, this.timeFrom, this.timeTo);
        // Only download tvg-logo if not resume
        if (!isResume) {
            java.io.File posterFile = new java.io.File(new java.io.File(outputFile).getParentFile(), "poster.jpg");
            getLogo(this.channelInfo != null ? this.channelInfo.tvgLogo() : null, this.channelInfo != null ? this.channelInfo.tvgName() : null, posterFile);
        }
        
        // Use ProcessBuilder with separate arguments to prevent command injection
        ProcessBuilder pb = new ProcessBuilder(
            "ffmpeg", "-hide_banner", "-loglevel", "panic", 
            "-i", this.url, "-c", "copy", outputFile
        );
        pb.redirectErrorStream(true);
        this.ffmpegProcess = pb.start();
        
        // Read ffmpeg output in a separate thread
        Thread ffmpegOutputThread = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(this.ffmpegProcess.getInputStream()))) {
                while (reader.readLine() != null) {
                    // Intentionally ignore output
                }
            } catch (Exception e) {
                LogHelper.LogWarning(TextHelper.FFMPEG_OUTPUT_THREAD_ERROR + e.getMessage());
            }
        });
        ffmpegOutputThread.setDaemon(true);
        ffmpegOutputThread.start();
        // Do NOT block the main thread with waitFor() here!
    }
	
	/**
	 * Performs a recording attempt, returns true if the stream lasted until stop time, otherwise false
	 */
	private boolean recordOnceRegular(String filePath, LocalTime targetTime) throws Exception {
        String outputFile = StringAndFileHelper.createFileNameWithSubfolder(filePath, LogHelper.getTimeZone(), this.channelInfo, this.timeFrom, this.timeTo);
        java.net.URL urlObj = java.net.URI.create(this.url).toURL();
        java.net.URLConnection conn = urlObj.openConnection();
        conn.setReadTimeout(60_000); // 60 sekunder timeout
        try (var input = conn.getInputStream();
             var outputStream = new java.io.FileOutputStream(new java.io.File(outputFile))) {
            byte[] bytes = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
                // Check if we've reached the stop time
                LocalTime currentTime = LocalTime.now();
                if (currentTime.isAfter(targetTime) || currentTime.equals(targetTime)) {
                    break;
                }
            }
            if (LocalTime.now().isBefore(targetTime)) {
                LogHelper.LogError(TextHelper.REGULAR_INPUTSTREAM_ENDED);
                return false;
            }
            return true;
        }
    }
	
	/**
	 * Starts regular recording using input/output streams
	 * @param filePath Destination path for the recording
	 * @throws Exception if recording cannot be started
	 */
	public void startRecRegular(String filePath, boolean isResume) throws Exception {
        recordingMode = RecordingMode.REGULAR;
        validateRecordingSetup(this.url, filePath);
        // Only download tvg-logo if not resume
        if (!isResume) {
            java.io.File posterFile = new java.io.File(new java.io.File(StringAndFileHelper.createFileNameWithSubfolder(filePath, LogHelper.getTimeZone(), this.channelInfo, this.timeFrom, this.timeTo)).getParentFile(), "poster.jpg");
            getLogo(this.channelInfo != null ? this.channelInfo.tvgLogo() : null, this.channelInfo != null ? this.channelInfo.tvgName() : null, posterFile);
        }

        LocalTime targetTime = DateTimeHelper.parseFlexibleLocalTime(this.timeTo, TIME_FORMATTER);

        while (LocalTime.now().isBefore(targetTime)) {
            try {
                boolean success = recordOnceRegular(filePath, targetTime);
                if (success) {
                    break; // Done!
                } else {
                    resumeRecordingProcess(filePath);
                    System.exit(1);
                }
            } catch (Exception e) {
                LogHelper.LogError(String.format(TextHelper.REGULAR_EXCEPTION_DURING_RECORDING, e.getMessage()), e);
                LogHelper.LogWarning(TextHelper.REGULAR_WAITING_BEFORE_RESUME);
                Thread.sleep(15_000);
                // The loop continues and tries again
            }
        }
    }
	
	/**
	 * Stops the current recording process
	 */
	public void stopRecording() {
        
        if (this.url == null || this.url.isEmpty()) {
            LogHelper.LogWarning(TextHelper.STOP_NO_URL_SET);
            return;
        }
        
        if (recordingMode == RecordingMode.REGULAR) {
            LogHelper.LogWarning(TextHelper.STOP_REGULAR_MODE_NO_SEPARATE_PROCESS);
            return;
        }
        
        // Only kill the correct ffmpeg process
        if (this.ffmpegProcess != null && this.ffmpegProcess.isAlive()) {
            this.ffmpegProcess.destroy();
            LogHelper.Log(TextHelper.STOPPED_FFMPEG_PROCESS_AND_ENDED_PROGRAM);
        } else {
            LogHelper.LogWarning(TextHelper.NO_FFMPEG_PROCESS_TO_KILL_OR_ALREADY_TERMINATED);
        }
    }

    // Remove myRunnable method and all references to it

	/**
	 * Downloads M3U file from URL with progress percentage if possible
	 * @param myUrl URL to download from
	 * @param path Destination path
	 * @param m3uName Name of the M3U file
	 */
	public static void getM3UFile(String myUrl, String path, String m3uName) {
        java.io.InputStream input = null;
        java.io.FileOutputStream outputStream = null;
        try {
            System.out.println(TextHelper.DOWNLOAD_M3U_PROGRESS);
            java.net.URL url = java.net.URI.create(myUrl).toURL();
            java.net.URLConnection conn = url.openConnection();
            int contentLength = conn.getContentLength();
            input = conn.getInputStream();
            outputStream = new FileOutputStream(new File(path + File.separator + m3uName));
            byte[] bytes = new byte[BUFFER_SIZE];
        int read;
            long totalRead = 0;
            int lastPercent = -1;
            boolean showProgress = contentLength > 0;
            if (!showProgress) {
                System.out.print(TextHelper.DOWNLOADING);
            }
        while ((read = input.read(bytes)) != -1) {
            outputStream.write(bytes, 0, read);
                if (showProgress) {
                    totalRead += read;
                    int percent = (int) ((totalRead * 100) / contentLength);
                    if (percent != lastPercent && percent <= 100) {
                        System.out.print(String.format(TextHelper.DOWNLOADING_PERCENT, percent));
                        lastPercent = percent;
                    }
                }
            }
            if (showProgress) {
                System.out.println(TextHelper.DOWNLOADING_DONE); // Clear line
            } else {
                System.out.println();
        }
    } catch (MalformedURLException e) {
            LogHelper.LogError(TextHelper.EXCEPTION_IN_RECORDERHELPER_GETM3UFILE, e);
        LogHelper.LogError(String.format(TextHelper.MALFORMED_URL, myUrl));
        LogHelper.LogError(LogHelper.printStackTrace(e));
    } catch (IOException e) {
            LogHelper.LogError(TextHelper.EXCEPTION_IN_RECORDERHELPER_GETM3UFILE, e);
        LogHelper.LogError(String.format(TextHelper.IO_ERROR_DURING_RECORDING, e.getMessage()));
        LogHelper.LogError(LogHelper.printStackTrace(e));
        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) {}
            try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) {}
    }
}
	
	/**
	 * Reads user input from console
	 * @return The trimmed user input string, or null if input is not available
	 */
	public String readInput() {
		
		String input = "";
		
		try {
			input = userIO.promptAndRead("");
		
			if (input.isEmpty()) {
			return "";
			} else {
			return input.trim();
			}
		} catch (NoSuchElementException e) {
            LogHelper.LogError(TextHelper.EXCEPTION_IN_READ_INPUT, e);
            LogHelper.LogError(TextHelper.NO_INPUT_AVAILABLE + e.getMessage());
            LogHelper.LogError(LogHelper.printStackTrace(e));
            return null;
        } catch (IllegalStateException e) {
            LogHelper.LogError(TextHelper.EXCEPTION_IN_READ_INPUT, e);
            LogHelper.LogError(TextHelper.SCANNER_CLOSED + e.getMessage());
            LogHelper.LogError(LogHelper.printStackTrace(e));
            return null;
        } catch (Exception e) {
            LogHelper.LogError(TextHelper.EXCEPTION_IN_READ_INPUT, e);
            LogHelper.LogError(TextHelper.UNKNOWN_ERROR_WHILE_READING_INPUT + e.getMessage());
            LogHelper.LogError(LogHelper.printStackTrace(e));
            return null;
		}	
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
	
	public String getTimeFrom() {
        return this.timeFrom;
    }
	
	public void setTimeFrom(String timeFrom) { this.timeFrom = timeFrom; }
	
	public String getUrl() { return this.url; }
    public String getTimeTo() { return this.timeTo; }
    public void setTimeTo(String timeTo) { this.timeTo = timeTo; }
    public void setUrl(String url) { this.url = url; }
    public void setChannelInfo(M3UHolder channelInfo) { this.channelInfo = channelInfo; }

    public static java.util.concurrent.ExecutorService getExecutor() {
        return executor;
    }

    // Add field for ffmpeg process
    private Process ffmpegProcess;
    private M3UHolder channelInfo;

    // Add fields for new arguments
    private String logConfigPath = "";
    private String timezone = "Europe/Stockholm";
    private boolean is24Hour = true;
    private String logFile = "";
    private String groupTitle = "";
    private String tvgId = "";
    private int recRetries = 3;
    private int recRetriesDelay = 60;
    private String tvgLogo = "";
    private String tvgName = "";

    // Getters and setters for new fields
    public void setLogConfigPath(String logConfigPath) { this.logConfigPath = logConfigPath; }
    public String getLogConfigPath() { return this.logConfigPath; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getTimezone() { return this.timezone; }
    public void setIs24Hour(boolean is24Hour) { this.is24Hour = is24Hour; }
    public boolean getIs24Hour() { return this.is24Hour; }
    public void setLogFile(String logFile) { this.logFile = logFile; }
    public String getLogFile() { return this.logFile; }
    public void setGroupTitle(String groupTitle) { this.groupTitle = groupTitle; }
    public String getGroupTitle() { return this.groupTitle; }
    public void setTvgId(String tvgId) { this.tvgId = tvgId; }
    public String getTvgId() { return this.tvgId; }
    public void setRecRetries(int recRetries) { this.recRetries = recRetries; }
    public int getRecRetries() { return this.recRetries; }
    public void setRecRetriesDelay(int recRetriesDelay) { this.recRetriesDelay = recRetriesDelay; }
    public int getRecRetriesDelay() { return this.recRetriesDelay; }
    public void setTvgLogo(String tvgLogo) { this.tvgLogo = tvgLogo; }
    public String getTvgLogo() { return this.tvgLogo; }
    public void setTvgName(String tvgName) { this.tvgName = tvgName; }
    public String getTvgName() { return this.tvgName; }

    /**
     * Builds the argument list for ScheduledRecorder using the original channelInfo and all required parameters.
     * This method is the single source of truth for argument construction.
     */
    public static java.util.List<String> buildScheduledRecorderArgsFromChannelInfo(M3UHolder channelInfo, String url, String filePath, String timeFrom, String timeTo, String logConfigPath, String timezone, boolean is24Hour, String logFile, int recRetries, int recRetriesDelay, boolean isResume) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(url); // 0
        args.add(filePath); // 1
        args.add(timeFrom); // 2
        args.add(timeTo); // 3
        args.add("regular"); // 4
        args.add(logConfigPath != null ? logConfigPath : ""); // 5
        args.add(channelInfo != null && channelInfo.tvgName() != null ? channelInfo.tvgName() : ""); // 6 (tvgName)
        args.add(timezone != null ? timezone : "Europe/Stockholm"); // 7
        args.add(Boolean.toString(is24Hour)); // 8
        args.add(logFile != null ? logFile : ""); // 9
        args.add(channelInfo != null && channelInfo.groupTitle() != null ? channelInfo.groupTitle() : ""); // 10 (groupTitle)
        args.add(channelInfo != null && channelInfo.tvgId() != null ? channelInfo.tvgId() : ""); // 11 (tvgId)
        args.add(Integer.toString(recRetries)); // 12
        args.add(Integer.toString(recRetriesDelay)); // 13
        args.add(channelInfo != null && channelInfo.tvgLogo() != null ? channelInfo.tvgLogo() : ""); // 14 (tvgLogo)
        args.add(Boolean.toString(isResume)); // 15
        return args;
    }

    // Resume ScheduledRecorder process med resume-flagga
    private void resumeRecordingProcess(String filePath) throws Exception {
        try {
            java.util.List<String> resumeArgs = new java.util.ArrayList<>();
            resumeArgs.add("java");
            resumeArgs.add("-cp");
            resumeArgs.add(System.getProperty("java.class.path"));
            resumeArgs.add("se.eskimos.recorder.ScheduledRecorder");
            java.util.List<String> scheduledArgs = buildScheduledRecorderArgsFromChannelInfo(
                this.channelInfo, this.url, filePath, this.timeFrom, this.timeTo, this.logConfigPath, this.timezone, this.is24Hour, this.logFile, this.recRetries, this.recRetriesDelay, true);
            resumeArgs.addAll(scheduledArgs);
            ProcessBuilder pb = new ProcessBuilder(resumeArgs);
            pb.inheritIO();
            Process process = pb.start();
            LogHelper.LogWarning(TextHelper.REGULAR_STARTED_RESUME_PROCESS);
            Thread.sleep(2000);
            try {
                int exitCode = process.exitValue();
                LogHelper.LogError(String.format(TextHelper.REGULAR_RESUMED_PROCESS_EXITED, exitCode));
            } catch (IllegalThreadStateException itse) {
                LogHelper.Log(TextHelper.REGULAR_RESUMED_PROCESS_RUNNING);
            }
        } catch (Exception ex) {
            System.err.println("Undantag i resumeRecordingProcess: " + ex.getMessage());
            LogHelper.LogError(TextHelper.REGULAR_FAILED_TO_RESUME + ex.getMessage(), ex);
            throw ex;
        }
    }

    // Download a logo image from a URL and save to dest (no progress print)
    public static void getLogo(String logoUrl, String channelName, File dest) {
        if (logoUrl != null && !logoUrl.isEmpty() && (logoUrl.startsWith("http://") || logoUrl.startsWith("https://"))) {
            java.io.InputStream input = null;
            java.io.FileOutputStream outputStream = null;
            try {
                java.net.URL url = java.net.URI.create(logoUrl).toURL();
                input = url.openStream();
                outputStream = new java.io.FileOutputStream(dest);
                byte[] bytes = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(bytes)) != -1) {
                    outputStream.write(bytes, 0, read);
                }
                LogHelper.Log(String.format(TextHelper.REGULAR_DOWNLOADED_TVG_LOGO, (channelName != null ? channelName : "?")));
            } catch (Exception e) {
                LogHelper.LogWarning(String.format(TextHelper.REGULAR_FAILED_TO_DOWNLOAD_TVG_LOGO, (channelName != null ? channelName : "?"), e.getMessage()));
            } finally {
                try { if (input != null) input.close(); } catch (Exception ignored) {}
                try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) {}
            }
        } else {
            LogHelper.Log(TextHelper.REGULAR_NO_TVG_LOGO_FOUND.replace("%s", (channelName != null ? channelName : "?")));
        }
    }
}
