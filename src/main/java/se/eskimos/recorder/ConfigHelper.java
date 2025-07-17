package se.eskimos.recorder;

import se.eskimos.log.LogHelper;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.ZoneId;


/**
 * Centralized configuration loader for IPTV-Recorder.
 * Loads config from config.properties and allows environment variable overrides.
 */
public class ConfigHelper {
    private final java.util.Properties props = new java.util.Properties();
    
    // Constants for default values
    private static final String DEFAULT_DESTINATION_PATH = "./recordings";
    private static final String DEFAULT_TIMEZONE = "Europe/Stockholm";
    private static final String DEFAULT_USE_FFMPEG = "false";
    private static final String DEFAULT_USE_M3U_FILE = "false";
    private static final String DEFAULT_EMPTY_STRING = "";
    private static final String GROUP_TITLE_SEPARATOR = "\\|";

    private boolean sendMail;
    private String sendTo;
    private String sentFrom;
    private String smtpHost;
    private String smtpPort;
    private String appPasswd;
    // Recording retry config
    private int recRetries;
    private int recRetriesDelay;

    /**
     * Creates a new ConfigHelper instance and loads configuration from the specified file
     * @param configPath Path to the configuration file
     */
    public ConfigHelper(String configPath) {
        try (var fis = new FileInputStream(configPath)) {
            props.load(fis);
        } catch (IOException e) {
            LogHelper.LogError(HelpText.FAILED_TO_LOAD_CONFIG + configPath);
            LogHelper.LogError(LogHelper.printStackTrace(e));
        }
        this.sendMail = Boolean.parseBoolean(props.getProperty("SENDMAIL", "false"));
        this.sendTo = props.getProperty("SENDTO", "");
        this.sentFrom = props.getProperty("SENTFROM", "");
        this.smtpHost = props.getProperty("SMTPHOST", "");
        this.smtpPort = props.getProperty("SMTPPORT", "");
        this.appPasswd = props.getProperty("APPPASSWD", "");
        // Read retry config with defaults
        try {
            this.recRetries = Integer.parseInt(props.getProperty("recRetries", "5"));
        } catch (NumberFormatException e) {
            this.recRetries = 5;
        }
        try {
            this.recRetriesDelay = Integer.parseInt(props.getProperty("recRetriesDelay", "60"));
        } catch (NumberFormatException e) {
            this.recRetriesDelay = 60;
        }
    }

    /**
     * Gets a value from environment variable or properties file, with fallback to default
     * @param key The configuration key
     * @param defaultValue The default value if not found
     * @return The configuration value
     */
    private String getEnvOrProp(String key, String defaultValue) {
        String env = System.getenv(key.toUpperCase());
        if (isValidString(env)) return env;
        
        String val = props.getProperty(key);
        if (isValidString(val)) return val;
        
        return defaultValue;
    }
    
    /**
     * Checks if a string is valid (not null and not empty after trimming)
     * @param str The string to check
     * @return true if the string is valid
     */
    private boolean isValidString(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    /**
     * Safely trims a string, returning empty string if null
     * @param str The string to trim
     * @return The trimmed string or empty string if null
     */
    private String safeTrim(String str) {
        return str != null ? str.trim() : DEFAULT_EMPTY_STRING;
    }

    /**
     * Gets the destination path for recordings
     * @return The destination path
     */
    public String getDestinationPath() {
        return getEnvOrProp("destinationPath", DEFAULT_DESTINATION_PATH);
    }
    
    /**
     * Gets the IPTV service URL
     * @return The URL or empty string if not configured
     */
    public String getUrl() {
        return getEnvOrProp("url", DEFAULT_EMPTY_STRING);
    }
    
    /**
     * Checks if FFMPEG should be used for recording
     * @return true if FFMPEG should be used
     */
    public boolean useFFMPEG() {
        return Boolean.parseBoolean(getEnvOrProp("useFFMPEG", DEFAULT_USE_FFMPEG));
    }
    
    /**
     * Checks if a local M3U file should be used
     * @return true if local M3U file should be used
     */
    public boolean useM3UFile() {
        return Boolean.parseBoolean(getEnvOrProp("useM3UFile", DEFAULT_USE_M3U_FILE));
    }
    
    /**
     * Gets the path to the M3U file
     * @return The M3U file path or empty string if not configured
     */
    public String getM3UFile() {
        return getEnvOrProp("m3uFile", DEFAULT_EMPTY_STRING);
    }
    
    /**
     * Gets the log file path
     * @return The log file path or null if not configured
     */
    public String getLogFile() {
        String file = props.getProperty("logFile");
        if (!isValidString(file)) {
            file = props.getProperty("logfile");
        }
        return isValidString(file) ? safeTrim(file) : null;
    }
    
    /**
     * Gets the group title filter
     * @return The group title or empty string if not configured
     */
    public String getGroupTitle() {
        return getEnvOrProp("GROUP_TITLE", DEFAULT_EMPTY_STRING);
    }

    /**
     * Gets the number of retries for scheduled recording
     */
    public int getRecRetries() {
        return recRetries;
    }

    /**
     * Gets the delay (in seconds) between retries for scheduled recording
     */
    public int getRecRetriesDelay() {
        return recRetriesDelay;
    }
    
    /**
     * Gets the group titles as an array
     * @return Array of group titles, empty array if none configured
     */
    public String[] getGroupTitles() {
        String raw = getEnvOrProp("GROUP_TITLE", DEFAULT_EMPTY_STRING);
        if (!isValidString(raw)) return new String[0];
        
        return java.util.Arrays.stream(raw.split(GROUP_TITLE_SEPARATOR))
            .map(String::trim)
            .filter(this::isValidString)
            .toArray(String[]::new);
    }
    
    /**
     * Checks if a group matches any of the specified group titles
     * @param group The group to check
     * @param groupTitles Array of group titles to match against
     * @return true if the group matches any of the group titles
     */
    public static boolean matchesAnyGroup(String group, String[] groupTitles) {
        if (group == null || groupTitles == null || groupTitles.length == 0) {
            return false;
        }
        
        String groupNorm = group.trim().toLowerCase();
        for (String g : groupTitles) {
            if (groupNorm.equals(g.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets the timezone for date/time operations
     * @return The timezone string
     */
    public String getTimezone() {
        String tz = props.getProperty("timezone");
        if (!isValidString(tz)) {
            return DEFAULT_TIMEZONE;
        }
        
        // Validate timezone format
        try {
            ZoneId.of(safeTrim(tz));
            return safeTrim(tz);
        } catch (Exception e) {
            LogHelper.LogWarning(String.format(HelpText.INVALID_TIMEZONE_FORMAT, tz, DEFAULT_TIMEZONE));
            return DEFAULT_TIMEZONE;
        }
    }
    
    /**
     * Checks if 24-hour clock format should be used
     * @return true if 24-hour clock should be used
     */
    public boolean is24HourClock() {
        String val = props.getProperty("24_hour_clock");
        return val == null || val.equalsIgnoreCase("true");
    }

    public boolean isSendMail() { return sendMail; }
    public String getSendTo() { return sendTo; }
    public String getSentFrom() { return sentFrom; }
    public String getSmtpHost() { return smtpHost; }
    public String getSmtpPort() { return smtpPort; }
    public String getAppPasswd() { return appPasswd; }

} 