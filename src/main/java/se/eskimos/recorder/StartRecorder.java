package se.eskimos.recorder;

import java.io.File;
import java.nio.file.Files;
import java.util.Scanner;
import se.eskimos.log.LogHelper;

public class StartRecorder {
	
	// Constants for magic numbers
	private static final String CONFIG_PATH = "config.properties";
	private static final String HELP_FLAG_1 = "--help";
	private static final String HELP_FLAG_2 = "-h";
	private static final String TEMP_FILE_PREFIX = "iptv-m3u-";
	private static final String TEMP_FILE_SUFFIX = ".m3u";
	
	public static final String ANSI_RED = "\u001B[31m";
	public static final String ANSI_GREEN = "\u001B[32m";
	public static final String ANSI_RESET = "\u001B[0m";

	private final UserInputHelper userIO;
	private final String mainHelpText;
	private final String exitChannelSelectionText;
	private final String backgroundProcessStartedText;
	private final String errorPrefixText;

	public StartRecorder(UserInputHelper userIO, String mainHelpText, String exitChannelSelectionText, String backgroundProcessStartedText, String errorPrefixText) {
		this.userIO = userIO;
		this.mainHelpText = mainHelpText;
		this.exitChannelSelectionText = exitChannelSelectionText;
		this.backgroundProcessStartedText = backgroundProcessStartedText;
		this.errorPrefixText = errorPrefixText;
	}

	/**
	 * Main entry point for the IPTV Recorder application
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		var userIO = new UserInputHelper(new Scanner(System.in), System.out);
		StartRecorder app = new StartRecorder(
			userIO,
			HelpText.MAIN_HELP,
			HelpText.EXIT_CHANNEL_SELECTION,
			HelpText.BACKGROUND_PROCESS_STARTED,
			HelpText.ERROR_PREFIX
		);
		app.run(args);
	}

	public void run(String[] args) {
		// --help CLI option
		if (args.length > 0 && (HELP_FLAG_1.equals(args[0]) || HELP_FLAG_2.equals(args[0]))) {
			userIO.print(mainHelpText);
			return;
		}

		// Special scenario: Direct search and start without interaction
		if (args.length > 1) {
			runSpecialScenario(args);
			return;
		}

		// Determine config file path
		String configPath = CONFIG_PATH;
		if (args.length > 0 && !args[0].startsWith("--")) {
			configPath = args[0];
		} else if (args.length > 1 && "--config".equals(args[0])) {
			configPath = args[1];
		}

		// Load config first
		ConfigHelper config = new ConfigHelper(configPath);
		String logFile = config.getLogFile();
		if (logFile != null && !logFile.isEmpty()) {
			LogHelper.setLogFile(logFile);
		}
		// Initialize mail buffer
		se.eskimos.recorder.MailExceptionBuffer.setConfig(config);
		// Now safe to log
		
		try {
			// Validate required configuration
			validateConfiguration(config, userIO);
			
			var rH = new RecorderHelper(userIO);
			var myChannels = new java.util.ArrayList<M3UHolder>();
			String destinationPath = config.getDestinationPath();
			boolean useM3UFile = config.useM3UFile();
			String m3uFile = config.getM3UFile();
			String url = config.getUrl();
			
			// Load channels from M3U file or URL
			myChannels = loadChannels(useM3UFile, m3uFile, url);
			
			// Filter channels by group title if specified
			String[] groupTitles = config.getGroupTitles();
			if (groupTitles.length > 0) {
				myChannels = filterChannelsByGroup(myChannels, groupTitles);
			}
			
			// Prompt user for channel selection and recording time (with paging)
			int startIndex = 0;
			while (true) {
				RecorderHelper.ChannelSelectionResult status;
				try {
					status = rH.loadChannels(myChannels, false, "", destinationPath, startIndex);
				} catch (RecorderHelper.UserCancelledException e) {
					userIO.print(exitChannelSelectionText);
					return;
				}
				if (status == RecorderHelper.ChannelSelectionResult.CHANNEL_SELECTED) {
					break;
				} else if (status == RecorderHelper.ChannelSelectionResult.NEXT_PAGE) {
					startIndex += 20;
					if (startIndex >= myChannels.size()) {
						startIndex = 0;
					}
				} else if (status == RecorderHelper.ChannelSelectionResult.QUIT) {
					userIO.print(exitChannelSelectionText);
					return;
				}
			}
			
			// After channel selection, prompt for time input as appropriate
			if (rH.getTimeFrom() == null || rH.getTimeFrom().isEmpty()) {
				rH.waitForTimeInput("\nPlease choose a starting time for the recording: \n", true);
			}
			rH.waitForTimeInput("\nPlease choose a stop time for the recording: \n", false);

			// Show and log summary
			M3UHolder selectedChannel = findSelectedChannel(myChannels, rH.getUrl());
			String channelDisplayName = getChannelDisplayName(selectedChannel);
			
			logRecordingSummary(channelDisplayName, rH, destinationPath);

			// Start ScheduledRecorder as a background process
			startScheduledRecorder(rH, config, channelDisplayName, selectedChannel);
			
			userIO.print(backgroundProcessStartedText);
			// After run, send summary mail if any error occurred
			se.eskimos.recorder.MailExceptionBuffer.flushAndSend();
			return;
		} catch (Exception e) {
			LogHelper.LogError("Application error: " + e.getMessage(), e);
			userIO.print(errorPrefixText + e.getMessage());
			// After run, send summary mail if any error occurred
			se.eskimos.recorder.MailExceptionBuffer.flushAndSend();
		}
	}
	
	// Special scenario: Searching and start without user interaction
	private void runSpecialScenario(String[] args) {
		// Expected: args[0]=config, args[1]=search string, args[2]=start time, args[3]=stop time
		if (args.length < 4) {
			System.err.println(HelpText.SPECIAL_SCENARIO_TOO_FEW_ARGS);
			return;
		}
		String configPath = args[0];
		String searchString = args[1];
		String argStartTime = args[2];
		String argStopTime = args[3];
		ConfigHelper config = new ConfigHelper(configPath);
		String logFile = config.getLogFile();
		if (logFile != null && !logFile.isEmpty()) {
			LogHelper.setLogFile(logFile);
		}
		try {
			validateConfiguration(config, userIO);
			var rH = new RecorderHelper(userIO);
			var myChannels = loadChannels(config.useM3UFile(), config.getM3UFile(), config.getUrl());
			String destinationPath = config.getDestinationPath();
			// Group filter if it exists
			String[] groupTitles = config.getGroupTitles();
			if (groupTitles.length > 0) {
				myChannels = filterChannelsByGroup(myChannels, groupTitles);
			}
			// Search directly
			java.util.List<M3UHolder> matches = se.eskimos.recorder.ChannelSearchHelper.searchChannels(myChannels, searchString);
			if (matches.isEmpty()) {
				System.err.println(String.format(HelpText.SPECIAL_SCENARIO_NO_MATCH, searchString));
				se.eskimos.recorder.MailExceptionBuffer.addException("StartRecorder.runSpecialScenario", "No channel matched the search: '" + searchString + "'");
				// Send mail if search failed
				new se.eskimos.recorder.MailHelper(config).sendMail(
					"IPTV-Recorder: No recording scheduled",
					"No channel matched the search: '" + searchString + "'\nNo recording was scheduled."
				);
				se.eskimos.recorder.MailExceptionBuffer.flushAndSend();
				return;
			}
			M3UHolder selected = matches.get(0);
			rH.setUrl(selected.url());
			// Extract time from tvg-name if possible
			String nameForTime = (selected.tvgName() != null && !selected.tvgName().isEmpty() ? selected.tvgName() : selected.name());
			java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2})").matcher(nameForTime);
			String startTime = argStartTime;
			if (matcher.find()) {
				startTime = matcher.group(1);
				LogHelper.Log(String.format(HelpText.SPECIAL_SCENARIO_START_TIME_EXTRACTED, startTime));
			}
			rH.setTimeFrom(startTime);
			rH.setTimeTo(argStopTime);
			// Start ScheduledRecorder as usual (or equivalent logic)
			String channelDisplayName = getChannelDisplayName(selected);
			logRecordingSummary(channelDisplayName, rH, destinationPath);
			startScheduledRecorder(rH, config, channelDisplayName, selected);
			userIO.print(backgroundProcessStartedText);
			// Send mail if recording is scheduled
			StringBuilder mailBody = new StringBuilder();
			mailBody.append("A new recording has been scheduled:\n\n");
			mailBody.append("Channel: ").append(channelDisplayName).append("\n");
			mailBody.append("Start time: ").append(rH.getTimeFrom()).append("\n");
			mailBody.append("Stop time: ").append(rH.getTimeTo()).append("\n");

			new se.eskimos.recorder.MailHelper(config).sendMail(
				"IPTV-Recorder: Recording scheduled",
				mailBody.toString()
			);
			// After special scenario, send summary mail if any error occurred
			se.eskimos.recorder.MailExceptionBuffer.flushAndSend();
			return;
		} catch (Exception e) {
			LogHelper.LogError("Application error: " + e.getMessage(), e);
			userIO.print(errorPrefixText + e.getMessage());
			// After special scenario, send summary mail if any error occurred
			se.eskimos.recorder.MailExceptionBuffer.flushAndSend();
		}
	}
	
	/**
	 * Validates the configuration for required parameters
	 * @param config The configuration helper
	 * @throws IllegalArgumentException if validation fails
	 */
	private static void validateConfiguration(ConfigHelper config, UserInputHelper userIO) {
		// destinationPath
		String destinationPath = config.getDestinationPath();
		if (destinationPath == null || destinationPath.isEmpty()) {
			String error = "Missing required config: destinationPath";
			LogHelper.LogError(error);
			userIO.print(error);
			throw new IllegalArgumentException(error);
		}
		// url
		String url = config.getUrl();
		if (url == null || url.isEmpty()) {
			String error = "Missing required config: url";
			LogHelper.LogError(error);
			userIO.print(error);
			throw new IllegalArgumentException(error);
		}
		// useFFMPEG
		String useFFMPEG = System.getenv("USEFFMPEG");
		if (useFFMPEG == null) useFFMPEG = config.useFFMPEG() ? "true" : "false";
		if (!useFFMPEG.equalsIgnoreCase("true") && !useFFMPEG.equalsIgnoreCase("false")) {
			String error = "Missing or invalid config: useFFMPEG (must be true or false)";
			LogHelper.LogError(error);
			userIO.print(error);
			throw new IllegalArgumentException(error);
		}
		// useM3UFile
		String useM3UFile = System.getenv("USEM3UFILE");
		if (useM3UFile == null) useM3UFile = config.useM3UFile() ? "true" : "false";
		if (!useM3UFile.equalsIgnoreCase("true") && !useM3UFile.equalsIgnoreCase("false")) {
			String error = "Missing or invalid config: useM3UFile (must be true or false)";
			LogHelper.LogError(error);
			userIO.print(error);
			throw new IllegalArgumentException(error);
		}
		// Om useM3UFile=true så är m3uFile obligatorisk
		if (useM3UFile.equalsIgnoreCase("true")) {
			String m3uFile = config.getM3UFile();
			if (m3uFile == null || m3uFile.isEmpty()) {
				String error = "Missing required config: m3uFile (required when useM3UFile=true)";
				LogHelper.LogError(error);
				userIO.print(error);
				throw new IllegalArgumentException(error);
			}
		}
		// recRetries
		int recRetries = config.getRecRetries();
		if (recRetries <= 0) {
			String error = "Missing or invalid config: recRetries (måste vara > 0)";
			LogHelper.LogError(error);
			userIO.print(error);
			throw new IllegalArgumentException(error);
		}
		// recRetriesDelay
		int recRetriesDelay = config.getRecRetriesDelay();
		if (recRetriesDelay <= 0) {
			String error = "Missing or invalid config: recRetriesDelay (måste vara > 0)";
			LogHelper.LogError(error);
			userIO.print(error);
			throw new IllegalArgumentException(error);
		}
		// If sendmail=true then alla other fields are mandatory
		if (config.isSendMail()) {
			if (config.getSendTo() == null || config.getSendTo().isEmpty()) {
				String error = "Missing required config: SENDTO (required when SENDMAIL=true)";
				LogHelper.LogError(error);
				userIO.print(error);
				throw new IllegalArgumentException(error);
			}
			if (config.getSentFrom() == null || config.getSentFrom().isEmpty()) {
				String error = "Missing required config: SENTFROM (required when SENDMAIL=true)";
				LogHelper.LogError(error);
				userIO.print(error);
				throw new IllegalArgumentException(error);
			}
			if (config.getSmtpHost() == null || config.getSmtpHost().isEmpty()) {
				String error = "Missing required config: SMTPHOST (required when SENDMAIL=true)";
				LogHelper.LogError(error);
				userIO.print(error);
				throw new IllegalArgumentException(error);
			}
			if (config.getSmtpPort() == null || config.getSmtpPort().isEmpty()) {
				String error = "Missing required config: SMTPPORT (required when SENDMAIL=true)";
				LogHelper.LogError(error);
				userIO.print(error);
				throw new IllegalArgumentException(error);
			}
			if (config.getAppPasswd() == null || config.getAppPasswd().isEmpty()) {
				String error = "Missing required config: APPPASSWD (required when SENDMAIL=true)";
				LogHelper.LogError(error);
				userIO.print(error);
				throw new IllegalArgumentException(error);
			}
		}
	}
	
	/**
	 * Loads channels from M3U file or downloads from URL
	 * @param useM3UFile Whether to use local M3U file
	 * @param m3uFile Path to M3U file
	 * @param url URL to download M3U from
	 * @return List of channels
	 * @throws Exception if loading fails
	 */
	private static java.util.ArrayList<M3UHolder> loadChannels(boolean useM3UFile, String m3uFile, String url) throws Exception {
		var myChannels = new java.util.ArrayList<M3UHolder>();
		
		if (useM3UFile) {
			var m3u = new M3UParser();
			myChannels = m3u.parseFile(new File(m3uFile));
		} else {
			// Download M3U to a temp file, parse, then delete
			File tempM3U = Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX).toFile();
			try {
				RecorderHelper.getM3UFile(url, tempM3U.getParent(), tempM3U.getName());
				var m3u = new M3UParser();
				myChannels = m3u.parseFile(tempM3U);
			} finally {
				tempM3U.delete();
			}
		}
		
		return myChannels;
	}
	
	/**
	 * Filters channels by group titles
	 * @param channels List of channels to filter
	 * @param groupTitles Group titles to match
	 * @return Filtered list of channels
	 */
	private static java.util.ArrayList<M3UHolder> filterChannelsByGroup(java.util.ArrayList<M3UHolder> channels, String[] groupTitles) {
		var filtered = new java.util.ArrayList<M3UHolder>();
		for (M3UHolder ch : channels) {
			if (ConfigHelper.matchesAnyGroup(ch.groupTitle(), groupTitles)) {
				filtered.add(ch);
			}
		}
		return filtered;
	}
	
	/**
	 * Finds the selected channel by URL
	 * @param channels List of available channels
	 * @param selectedUrl URL of the selected channel
	 * @return The selected channel or null if not found
	 */
	private static M3UHolder findSelectedChannel(java.util.ArrayList<M3UHolder> channels, String selectedUrl) {
		return channels.stream()
			.filter(ch -> ch.url().equalsIgnoreCase(selectedUrl))
			.findFirst()
			.orElse(null);
	}
	
	/**
	 * Gets the display name for a channel
	 * @param channel The channel to get display name for
	 * @return The display name
	 */
	private static String getChannelDisplayName(M3UHolder channel) {
		if (channel == null) {
			return "";
		}
		return (channel.tvgName() != null && !channel.tvgName().isEmpty()) ? channel.tvgName() : channel.name();
	}
	
	/**
	 * Logs the recording summary
	 * @param channelDisplayName Name of the selected channel
	 * @param rH Recorder helper instance
	 * @param destinationPath Destination path for recordings
	 */
	private static void logRecordingSummary(String channelDisplayName, RecorderHelper rH, String destinationPath) {
		String[] summaryLines = {
			"--- Summary ---",
			"Channel: " + channelDisplayName,
			"Start time: " + rH.getTimeFrom(),
			"Stop time: " + rH.getTimeTo(),
			"URL: " + rH.getUrl(),
			"Destination: " + destinationPath
		};
		for (String line : summaryLines) {
			LogHelper.LogScheduler(line);
		}
	}
	
	/**
	 * Starts the ScheduledRecorder as a background process
	 * @param rH Recorder helper instance
	 * @param config Configuration helper
	 * @param channelDisplayName Display name of the selected channel
	 * @param selectedChannel Selected channel information
	 * @throws Exception if process start fails
	 */
	private static void startScheduledRecorder(RecorderHelper rH, ConfigHelper config, String channelDisplayName, M3UHolder selectedChannel) throws Exception {
		// Parametrar är redan validerade i validateConfiguration
		int recRetries = config.getRecRetries();
		int recRetriesDelay = config.getRecRetriesDelay();

		String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		String classpath = System.getProperty("java.class.path");
		String mode = config.useFFMPEG() ? "ffmpeg" : "regular";
		String timezone = config.getTimezone();
		boolean is24Hour = config.is24HourClock();
		String logFile = config.getLogFile();
		
		if (logFile != null) {
			LogHelper.setLogFile(logFile);
		}
		LogHelper.setTimeZone(java.time.ZoneId.of(timezone));
		
		java.util.List<String> cmd = new java.util.ArrayList<>();
		cmd.add(javaBin); cmd.add("-cp"); cmd.add(classpath);
		cmd.add("se.eskimos.recorder.ScheduledRecorder");
		cmd.add(rH.getUrl());                        // 0
		cmd.add(config.getDestinationPath());        // 1
		cmd.add(rH.getTimeFrom());                   // 2
		cmd.add(rH.getTimeTo());                     // 3
		cmd.add(mode);                               // 4
		cmd.add(channelDisplayName);                 // 5
		cmd.add(timezone);                           // 6
		cmd.add(Boolean.toString(is24Hour));         // 7
		cmd.add(logFile == null ? "" : logFile);     // 8
		cmd.add(selectedChannel != null ? selectedChannel.groupTitle() : ""); // 9
		cmd.add(selectedChannel != null ? selectedChannel.tvgId() : "");      // 10
		// Add recRetries and recRetriesDelay as arguments (11, 12)
		cmd.add(Integer.toString(recRetries)); // 11
		cmd.add(Integer.toString(recRetriesDelay)); // 12
		// Add tvgLogo as argument 13
		cmd.add(selectedChannel != null ? selectedChannel.tvgLogo() : ""); // 13
		
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.inheritIO(); // Optional: inherit IO for debug, or redirect to log
		Process process = pb.start();
		
		// Validate that process started successfully
		if (!process.isAlive()) {
			throw new RuntimeException("Failed to start background recording process");
		}
	}
}
