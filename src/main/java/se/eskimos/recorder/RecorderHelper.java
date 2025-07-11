package se.eskimos.recorder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import se.eskimos.log.LogHelper;
import java.util.Properties;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.NoSuchElementException;

public class RecorderHelper {
	
	// Constants for magic numbers
	private static final int DISPLAY_LENGTH_ACCEPTED = 160;
	private static final int BUFFER_SIZE = 8192;
	private static final int SLEEP_INTERVAL_MS = 1000;
	private static final String DEFAULT_GROUP_NAME = "Unknown";
	private static final String FILE_EXTENSION = ".ts";
	private static final String PLUS_REPLACEMENT = "plus";
	private static final int CHANNELS_PER_PAGE = 20;
	private static final int DISPLAY_CODE_LENGTH_ADJUSTMENT = 18;
	private static final int MAX_FILENAME_LENGTH = 255;
	private static final long MIN_DISK_SPACE_BYTES = 1024 * 1024 * 100; // 100MB minimum
	
	// Remove static fields and make them instance variables (except fileSeparator, which must remain static)
	private static String fileSeparator = File.separator;
	
	public static final String ANSI_RESET = "\u001B[0m";
	
	private String timeFrom = "";
	private String timeTo = "";
	private String url = "";
	private String errorMessage = "";
	
	private static String OS = System.getProperty("os.name").toLowerCase();
	
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
	private static final SimpleDateFormat LEGACY_TIME_SECONDS_FORMATTER = new SimpleDateFormat("HH:mm:ss");
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	/**
	 * Loads properties from config.properties file
	 * @param fileName Path to the properties file
	 * @return Properties object containing the configuration
	 * @throws IOException if the file cannot be read
	 */
	public Properties readPropertiesFile(String fileName) throws IOException {
	    Properties prop = new Properties();
	    try (var fis = new FileInputStream(fileName)) {
	        prop.load(fis);
	    } catch(FileNotFoundException fnfe) {
	        LogHelper.LogError("Failed to load properties file: " + fileName);
	        LogHelper.LogError(LogHelper.printStackTrace(fnfe));
	    } catch(IOException ioe) {
	        LogHelper.LogError("IO error while loading properties file: " + fileName);
	        LogHelper.LogError(LogHelper.printStackTrace(ioe));
	    }
	    return prop;
	}
	
	/**
	 * Sanitizes a string for use in file/folder names by replacing special characters with underscores
	 * @param input The string to sanitize
	 * @return Sanitized string safe for file/folder names
	 */
	public static String sanitizeForFileName(String input) {
		if (input == null || input.isEmpty()) {
			return DEFAULT_GROUP_NAME;
		}
		return input.replaceAll("[\\s]+", "_")
					.replaceAll("[^a-zA-Z0-9_]", "_")
					.replaceAll("_+", "_")
					.replaceAll("^_+|_+$", "");
	}
	
	/**
	 * Validates URL format and disk space availability
	 * @param url The URL to validate
	 * @param destinationPath The destination path to check disk space
	 * @throws IllegalArgumentException if validation fails
	 */
	private void validateRecordingSetup(String url, String destinationPath) {
		// Validate URL format
		if (url == null || url.trim().isEmpty()) {
			throw new IllegalArgumentException(HelpText.URL_CANNOT_BE_NULL);
		}
		
		try {
			new java.net.URI(url);
		} catch (java.net.URISyntaxException e) {
			throw new IllegalArgumentException(String.format(HelpText.INVALID_URL_FORMAT, url));
		}
		
		// Check disk space
		File destinationDir = new File(destinationPath);
		if (!destinationDir.exists()) {
			destinationDir.mkdirs();
		}
		
		long freeSpace = destinationDir.getFreeSpace();
		if (freeSpace < MIN_DISK_SPACE_BYTES) {
			throw new IllegalArgumentException(String.format(HelpText.INSUFFICIENT_DISK_SPACE, (freeSpace / (1024 * 1024)), (MIN_DISK_SPACE_BYTES / (1024 * 1024))));
		}
	}
	
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
        clearScreen();
		 int counter = 0;
        int totalChannels = myChannels.size();
        for (int i = startIndex; i < totalChannels && counter < CHANNELS_PER_PAGE; i++) {
            M3UHolder mH = myChannels.get(i);
			 String displayName = "Channel name: " + (mH.tvgName() != null && !mH.tvgName().isEmpty() ? mH.tvgName().trim() : mH.name().trim());
			 String displayCode = "Channel code: " + mH.code().trim();
			 int totalOfBoth = displayName.length() + displayCode.length();
            int lengthOfSpaces = DISPLAY_LENGTH_ACCEPTED - totalOfBoth;
            if (displayCode.length() == DISPLAY_CODE_LENGTH_ADJUSTMENT) {
                lengthOfSpaces = lengthOfSpaces - 1;
            }
			 String space = StringHelper.createSpaces(lengthOfSpaces);
			 int lengthTotal = displayName.length() + space.length() + displayCode.length();
			 String underLine = StringHelper.createUnderLine(lengthTotal);
			 System.out.println(displayName + space + displayCode);
			 System.out.println(underLine);
			 counter++;
        }
        // If there are more channels, ask for input
        if (startIndex + CHANNELS_PER_PAGE < totalChannels) {
            ChannelSelectionResult result = waitForChannelInput(myChannels, restarted, text, destinationPath, startIndex);
            if (result == ChannelSelectionResult.RESTART_MAIN_PROMPT) {
                clearScreen(); // Ensure list is shown again after failed search
                return loadChannels(myChannels, restarted, text, destinationPath, startIndex);
            }
            return result;
        } else {
            // Last page, ask for input but don't increment page further
            ChannelSelectionResult result = waitForChannelInput(myChannels, restarted, text, destinationPath, startIndex);
            if (result == ChannelSelectionResult.RESTART_MAIN_PROMPT) {
                clearScreen(); // Ensure list is shown again after failed search
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

    private final UserInputHelper userIO;
    
    /**
     * Constructs a RecorderHelper with a given UserInputHelper and HelpText for testable input/output and messages.
     */
    public RecorderHelper(UserInputHelper userIO) {
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
                input = userIO.promptAndRead(HelpText.PROMPT_CHANNEL_SELECTION);
            } else {
                forcePrompt = false;
            }
            if (input == null) {
                LogHelper.LogError(HelpText.USER_INPUT_NULL);
                throw new UserCancelledException(HelpText.USER_INPUT_NULL);
            }
            if (input.isEmpty()) {
                return ChannelSelectionResult.NEXT_PAGE;
            }
            if (input.equalsIgnoreCase("q")) {
                LogHelper.Log(HelpText.USER_CHOSE_QUIT);
                throw new UserCancelledException(HelpText.USER_CHOSE_QUIT);
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
                    LogHelper.Log(HelpText.USER_CHOSE_QUIT_SEARCH);
                    throw new UserCancelledException(HelpText.USER_CHOSE_QUIT_SEARCH);
                } else if (searchResult == ChannelSelectionResult.NEXT_PAGE) {
                    return ChannelSelectionResult.NEXT_PAGE;
                }
            }
            if (done) {
                break;
            } else {
                setErrorMessage(HelpText.INVALID_CHANNEL_SELECTION);
                LogHelper.LogError(HelpText.INVALID_CHANNEL_SELECTION);
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
                    LogHelper.Log(String.format(HelpText.START_TIME_EXTRACTED, this.timeFrom));
                    userIO.print(String.format(HelpText.START_TIME_EXTRACTED, this.timeFrom));
                } else {
                    this.timeFrom = "";
                }
                createFileName(destinationPath, LogHelper.getTimeZone(), mH);
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
                String retryInput = userIO.promptAndRead(String.format(HelpText.NO_CHANNELS_FOUND, input));
                if (retryInput == null) {
                    LogHelper.LogError(HelpText.USER_INPUT_NULL_SEARCH);
                    throw new UserCancelledException(HelpText.USER_INPUT_NULL_SEARCH);
                }
                if (retryInput.isEmpty()) {
                    // Only now, when user presses ENTER, return to main list (do NOT clear screen)
                    return ChannelSelectionResult.NEXT_PAGE;
                }
                if (retryInput.equalsIgnoreCase("q")) {
                    LogHelper.Log(HelpText.USER_CHOSE_QUIT_SEARCH_RETRY);
                    throw new UserCancelledException(HelpText.USER_CHOSE_QUIT_SEARCH_RETRY);
                }
                // Otherwise: new search string, do a new search
                input = retryInput;
                continue;
            } else {
                displaySearchResults(matches, input);
                String searchInput = userIO.promptAndRead(HelpText.PROMPT_SEARCH_RESULTS);
                if (searchInput == null) {
                    LogHelper.LogError(HelpText.USER_INPUT_NULL_SEARCH_RESULT);
                    throw new UserCancelledException(HelpText.USER_INPUT_NULL_SEARCH_RESULT);
                }
                if (searchInput.isEmpty()) {
                    return ChannelSelectionResult.NEXT_PAGE;
                }
                if (searchInput.equalsIgnoreCase("q")) {
                    LogHelper.Log(HelpText.USER_CHOSE_QUIT_SEARCH);
                    throw new UserCancelledException(HelpText.USER_CHOSE_QUIT_SEARCH);
                }
                if (isNumeric(searchInput)) {
                    for (M3UHolder mH : matches) {
                        if (mH.code().equalsIgnoreCase(searchInput)) {
                            this.url = mH.url();
                            String nameForTime = (mH.tvgName() != null && !mH.tvgName().isEmpty() ? mH.tvgName() : mH.name());
                            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2})").matcher(nameForTime);
                            if (matcher.find()) {
                                this.timeFrom = matcher.group(1);
                                LogHelper.Log(String.format(HelpText.START_TIME_EXTRACTED, this.timeFrom));
                                userIO.print(String.format(HelpText.START_TIME_EXTRACTED, this.timeFrom));
                            } else {
                                this.timeFrom = "";
                            }
                            createFileName(destinationPath, LogHelper.getTimeZone(), mH);
                            return ChannelSelectionResult.CHANNEL_SELECTED;
                        }
                    }
                    // If no matching channel number was found
                    userIO.print(HelpText.CHANNEL_CODE_NOT_FOUND);
                    continue;
                }
                // Otherwise: new search string, do a new search
                input = searchInput;
            }
        }
    }

    /**
     * Displays up to 20 search results using UserInputHelper.
     */
    private void displaySearchResults(java.util.List<M3UHolder> matches, String input) {
        userIO.print(String.format(HelpText.SEARCH_RESULTS_HEADER, input));
        int shown = 0;
        for (M3UHolder mH : matches) {
            if (shown >= 20) break;
            String displayName = "Channel name: " + (mH.tvgName() != null && !mH.tvgName().isEmpty() ? mH.tvgName().trim() : mH.name().trim());
            String displayCode = "Channel code: " + mH.code().trim();
            int totalOfBoth = displayName.length() + displayCode.length();
            int lengthOfSpaces = DISPLAY_LENGTH_ACCEPTED - totalOfBoth;
            if (displayCode.length() == DISPLAY_CODE_LENGTH_ADJUSTMENT) {
                lengthOfSpaces = lengthOfSpaces - 1;
            }
            String space = StringHelper.createSpaces(lengthOfSpaces);
            int lengthTotal = displayName.length() + space.length() + displayCode.length();
            String underLine = StringHelper.createUnderLine(lengthTotal);
            userIO.print(displayName + space + displayCode);
            userIO.print(underLine);
            shown++;
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
			userIO.print(String.format(HelpText.START_TIME_ALREADY_SET, this.timeFrom));
			userIO.print(HelpText.PRESS_ENTER_TO_CONTINUE);
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
				userIO.print(HelpText.WRONG_TIME_FORMAT);
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
		        LocalTime.parse(time);
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
	 * Clears the console screen based on operating system
	 */
	public static void clearScreen(){
		
		if (OS.contains("win")) {
			myRunnable("cls", OS);
        }else {
        	System.out.print("\033\143"); // Only print to terminal, do not log
        }
	}
	
	/**
	 * Waits until the start time is reached
	 */
	public void startCounter() {
		
		LocalTime targetTime = LocalTime.parse(this.timeFrom, TIME_FORMATTER);
		 
		while(true) {
			 
			LocalTime currentTime = LocalTime.now();
			if (currentTime.isAfter(targetTime) || currentTime.equals(targetTime)){
				break;
			}
			
			displayRecordingStatus(HelpText.WAITING_FOR_RECORDING_START, true);
 
			try {
				Thread.sleep(SLEEP_INTERVAL_MS);
			} catch (InterruptedException e) {
				LogHelper.LogWarning(HelpText.START_COUNTER_INTERRUPTED);
			}
		}
	}
	
	/**
	 * Waits until the end time is reached
	 */
	public void endCounter() {
		
		LocalTime targetTime = LocalTime.parse(this.timeTo, TIME_FORMATTER);
		 
		while(true) {
			 
			LocalTime currentTime = LocalTime.now();
			if (currentTime.isAfter(targetTime) || currentTime.equals(targetTime)){
				break;
			}
			
			displayRecordingStatus(HelpText.RECORDING_STARTED_WAITING_FOR_END, false);
 
			try {
				Thread.sleep(SLEEP_INTERVAL_MS);
			} catch (InterruptedException e) {
				LogHelper.LogWarning(HelpText.END_COUNTER_INTERRUPTED);
			}
		}
	}
	
	/**
	 * Common method to display recording status information
	 * @param text Status text to display
	 * @param isStart Whether this is for start display (affects log level)
	 */
	private void displayRecordingStatus(String text, boolean isStart) {
		clearScreen();
		
		String displayTime = LEGACY_TIME_SECONDS_FORMATTER.format(new Date());
		
		LogHelper.Log("\n" + HelpText.RECORDING_STARTIME + this.timeFrom.trim());
		LogHelper.Log(HelpText.RECORDING_STOPTIME + this.timeTo.trim());
		LogHelper.Log(HelpText.URL_OF_CHANNEL + this.url.trim() + "\n");
		
		if (isStart) {
			LogHelper.LogWarning("\n" + text + displayTime);
		} else {
		LogHelper.Log(text + displayTime);
		}
	}
	
	/**
	 * Starts FFMPEG recording process
	 * @param filePath Destination path for the recording
	 * @throws Exception if recording cannot be started
	 */
	public void startRecFFMPEG(String filePath) throws Exception {
        recordingMode = RecordingMode.FFMPEG;
        validateRecordingSetup(this.url, filePath);
        String outputFile = createFileName(filePath, LogHelper.getTimeZone(), this.channelInfo);
        
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
                LogHelper.LogWarning(HelpText.FFMPEG_OUTPUT_THREAD_ERROR + e.getMessage());
            }
        });
        ffmpegOutputThread.setDaemon(true);
        ffmpegOutputThread.start();
        // Do NOT block the main thread with waitFor() here!
    }
	
	/**
	 * Starts regular recording using input/output streams
	 * @param filePath Destination path for the recording
	 * @throws Exception if recording cannot be started
	 */
	public void startRecRegular(String filePath) throws Exception {
        recordingMode = RecordingMode.REGULAR;
        validateRecordingSetup(this.url, filePath);
        
        LocalTime targetTime = LocalTime.parse(this.timeTo, TIME_FORMATTER);
        
        try (var input = java.net.URI.create(this.url).toURL().openStream();
             var outputStream = new java.io.FileOutputStream(new java.io.File(createFileName(filePath, LogHelper.getTimeZone(), this.channelInfo)))) {
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
        } catch (java.net.MalformedURLException e) {
            LogHelper.LogError(HelpText.MALFORMED_URL + this.url);
            throw e;
        } catch (java.io.IOException e) {
            LogHelper.LogError(HelpText.IO_ERROR_DURING_REGULAR_RECORDING + e.getMessage());
            throw e;
        }
    }
	
	/**
	 * Creates a filename for the recording based on channel information and timing
	 * @param filePath Base path for the recording
	 * @param zone Timezone for date formatting
	 * @param channel Channel information
	 * @return Full path to the recording file
	 */
	public String createFileName(String filePath, java.time.ZoneId zone, M3UHolder channel) {
        String date = DATE_FORMATTER.withZone(zone).format(java.time.ZonedDateTime.now(zone));
        String tvgName = channel != null && channel.tvgName() != null && !channel.tvgName().isEmpty() ? channel.tvgName().trim() : null;
        String groupTitle = channel != null && channel.groupTitle() != null ? channel.groupTitle().trim() : "";
       
        String folder;
        String fileNameBase;
        String group = sanitizeForFileName(groupTitle);
        String start = (this.timeFrom != null) ? this.timeFrom.replace(":", "") : "";
        String stop = (this.timeTo != null) ? this.timeTo.replace(":", "") : "";
        String sportPart = "";
        
        if (channel != null && (channel.tvgId() == null || channel.tvgId().isEmpty())) {
            // Directory name = group-title, filename = group-title[_Sport1_Sport2_Stage]_YYYY-MM-DD_START_STOP.ts
            folder = group;
            if (tvgName != null && !tvgName.isEmpty()) {
                String[] matches = se.eskimos.recorder.SportsEventsHelper.extractAllEventsAndStages(tvgName);
                if (matches.length > 0) {
                    sportPart = "_" + String.join("_", matches);
                }
            }
            fileNameBase = group + sportPart + "_" + date + "_" + start + "_" + stop;
        } else if (tvgName != null && !tvgName.isEmpty()) {
            // If tvg-id exists, use tvg-name for directory/filename, no sport matching
            folder = sanitizeForFileName(tvgName);
            fileNameBase = folder + "_" + date + "_" + start + "_" + stop;
        } else {
            // Fallback
            folder = group;
            fileNameBase = group + "_" + date + "_" + start + "_" + stop;
        }
        
        // Validate filename length
        if (fileNameBase.length() > MAX_FILENAME_LENGTH - FILE_EXTENSION.length()) {
            fileNameBase = fileNameBase.substring(0, MAX_FILENAME_LENGTH - FILE_EXTENSION.length());
        }
        
        java.io.File eventDir = new java.io.File(filePath, folder);
        if (!eventDir.exists()) eventDir.mkdirs();
        String fileName = fileNameBase + FILE_EXTENSION;
        fileName = fileName.replace("+", PLUS_REPLACEMENT);
        return new java.io.File(eventDir, fileName).getAbsolutePath();
    }
	
	/**
	 * Stops the current recording process
	 */
	public void stopRecording() {
        
        if (this.url == null || this.url.isEmpty()) {
            LogHelper.LogWarning(HelpText.STOP_NO_URL_SET);
            return;
        }
        
        if (recordingMode == RecordingMode.REGULAR) {
            LogHelper.LogWarning(HelpText.STOP_REGULAR_MODE_NO_SEPARATE_PROCESS);
            return;
        }
        
        // Only kill the correct ffmpeg process
        if (this.ffmpegProcess != null && this.ffmpegProcess.isAlive()) {
            this.ffmpegProcess.destroy();
            LogHelper.Log(HelpText.STOPPED_FFMPEG_PROCESS_AND_ENDED_PROGRAM);
        } else {
            LogHelper.LogWarning(HelpText.NO_FFMPEG_PROCESS_TO_KILL_OR_ALREADY_TERMINATED);
        }
    }

    private static void myRunnable(String command, String OS) {
        LogHelper.Log("[STOP][DEBUG] Running myRunnable with command: '" + command + "' and OS: '" + OS + "'");
        if (OS.contains("win")) {
            try {
                LogHelper.Log("[STOP][DEBUG] Windows: Starting process: cmd /c " + command);
                Process p = new ProcessBuilder("cmd", "/c", command).inheritIO().start();
                int exitCode = p.waitFor();
                LogHelper.Log("[STOP][DEBUG] Windows process exited with exit code: " + exitCode);
            } catch (InterruptedException | IOException e) {
                LogHelper.LogError("Exception in RecorderHelper.myRunnable", e);
            }
        } else {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", command);
            try {
                LogHelper.Log("[STOP][DEBUG] Linux: Starting process: bash -c '" + command + "'");
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                LogHelper.Log("[STOP][DEBUG] Linux process exited with exit code: " + exitCode);
            } catch (IOException e) {
                LogHelper.LogError("Exception in RecorderHelper.myRunnable", e);
            } catch (InterruptedException e) {
                LogHelper.LogError("Exception in RecorderHelper.myRunnable", e);
            }
        }
    }
	
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
            System.out.println(HelpText.DOWNLOAD_M3U_PROGRESS);
            java.net.URL url = java.net.URI.create(myUrl).toURL();
            java.net.URLConnection conn = url.openConnection();
            int contentLength = conn.getContentLength();
            input = conn.getInputStream();
            outputStream = new FileOutputStream(new File(path + fileSeparator + m3uName));
            byte[] bytes = new byte[BUFFER_SIZE];
        int read;
            long totalRead = 0;
            int lastPercent = -1;
            boolean showProgress = contentLength > 0;
            if (!showProgress) {
                System.out.print(HelpText.DOWNLOADING);
            }
        while ((read = input.read(bytes)) != -1) {
            outputStream.write(bytes, 0, read);
                if (showProgress) {
                    totalRead += read;
                    int percent = (int) ((totalRead * 100) / contentLength);
                    if (percent != lastPercent && percent <= 100) {
                        System.out.print(String.format(HelpText.DOWNLOADING_PERCENT, percent));
                        lastPercent = percent;
                    }
                }
            }
            if (showProgress) {
                System.out.println(HelpText.DOWNLOADING_DONE); // Clear line
            } else {
                System.out.println();
        }
    } catch (MalformedURLException e) {
            LogHelper.LogError("Exception in RecorderHelper.getM3UFile", e);
        LogHelper.LogError(String.format(HelpText.MALFORMED_URL, myUrl));
        LogHelper.LogError(LogHelper.printStackTrace(e));
    } catch (IOException e) {
            LogHelper.LogError("Exception in RecorderHelper.getM3UFile", e);
        LogHelper.LogError(String.format(HelpText.IO_ERROR_DURING_RECORDING, e.getMessage()));
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
            LogHelper.LogError("Exception in RecorderHelper.readInput", e);
            LogHelper.LogError("[INPUT] Ingen inmatning tillgänglig (NoSuchElementException): " + e.getMessage());
            LogHelper.LogError(LogHelper.printStackTrace(e));
            return null;
        } catch (IllegalStateException e) {
            LogHelper.LogError("Exception in RecorderHelper.readInput", e);
            LogHelper.LogError("[INPUT] Scanner är stängd (IllegalStateException): " + e.getMessage());
            LogHelper.LogError(LogHelper.printStackTrace(e));
            return null;
        } catch (Exception e) {
            LogHelper.LogError("Exception in RecorderHelper.readInput", e);
            LogHelper.LogError("[INPUT] Okänt fel vid inläsning av input: " + e.getMessage());
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
}
