package se.eskimos.recorder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import se.eskimos.log.LogHelper;

public class M3UParser {

	// Constants for magic numbers and patterns
	private static final String UNKNOWN_CHANNEL_NAME = "Unknown";
	private static final String EXTINF_PREFIX = "#EXTINF";
	private static final String GROUP_TITLE_PATTERN = "group-title=\"(.*?)\"";
	private static final String TVG_ID_PATTERN = "tvg-id=\"(.*?)\"";
	private static final String TVG_NAME_PATTERN = "tvg-name=\"(.*?)\"";
	private static final String TVG_LOGO_PATTERN = "tvg-logo=\"(.*?)\"";
	
	// Static compiled patterns for efficiency
	private static final Pattern GROUP_TITLE_REGEX = Pattern.compile(GROUP_TITLE_PATTERN, Pattern.CASE_INSENSITIVE);
	private static final Pattern TVG_ID_REGEX = Pattern.compile(TVG_ID_PATTERN, Pattern.CASE_INSENSITIVE);
	private static final Pattern TVG_NAME_REGEX = Pattern.compile(TVG_NAME_PATTERN, Pattern.CASE_INSENSITIVE);
	private static final Pattern TVG_LOGO_REGEX = Pattern.compile(TVG_LOGO_PATTERN, Pattern.CASE_INSENSITIVE);

	public M3UParser() throws Exception {

	}

	/**
	 * Parses an M3U file and extracts channel information
	 * @param f The M3U file to parse
	 * @return List of M3UHolder objects containing channel information
	 * @throws FileNotFoundException if the file doesn't exist
	 */
	public ArrayList<M3UHolder> parseFile(File f) throws FileNotFoundException {
		ArrayList<M3UHolder> myArray = new ArrayList<>();
		if (f.exists()) {
			try (BufferedReader br = new BufferedReader(new FileReader(f))) {
				String line;
				String name = "";
				String url = "";
				String code = "";
				String groupTitle = "";
				String tvgId = "";
				String tvgName = "";
                String tvgLogo = "";
				while ((line = br.readLine()) != null) {
					if (line.startsWith(EXTINF_PREFIX)) {
						name = extractChannelName(line);
						groupTitle = extractGroupTitle(line);
						tvgId = extractTvgId(line);
						tvgName = extractTvgName(line);
                        tvgLogo = extractTvgLogo(line);
						// Next line should be the URL
						url = br.readLine();
						if (url != null && !url.trim().isEmpty()) {
							// --- New logic for channelCode extraction ---
							String urlForCode = url;
							int lastDot = url.lastIndexOf('.');
							if (lastDot > url.lastIndexOf('/')) {
								urlForCode = url.substring(0, lastDot);
							}
							code = lastBigInteger(urlForCode).toString();
							// --- End new logic ---
							myArray.add(new M3UHolder(name, url, code, groupTitle, tvgId, tvgName, tvgLogo));
						}
					}
				}
			} catch (FileNotFoundException e) {
				LogHelper.LogError(HelpText.M3U_FILE_NOT_FOUND + f.getAbsolutePath());
				throw e;
			} catch (Exception e) {
				LogHelper.LogError(HelpText.FAILED_TO_PARSE_M3U_FILE + f.getAbsolutePath());
				LogHelper.LogError(HelpText.ERROR_PREFIX + e.getMessage());
				LogHelper.LogError(LogHelper.printStackTrace(e));
			}
		}
		return myArray;
	}

	/**
	 * Extracts the last sequence of digits from a URL string
	 * @param s The URL string to process
	 * @return BigInteger representing the extracted digits, or ZERO if none found
	 */
	private static BigInteger lastBigInteger(String s) {
	    if (s == null || s.isEmpty()) {
	        return BigInteger.ZERO;
	    }
	    
	    int i = s.length();
	    while (i > 0 && Character.isDigit(s.charAt(i - 1))) {
	        i--;
	    }
	    String digits = s.substring(i);
	    if (digits.isEmpty()) {
	        return BigInteger.ZERO;
	    }
	    return new BigInteger(digits);
	}

	/**
	 * Extracts group-title from EXTINF line
	 * @param extinfLine The EXTINF line to parse
	 * @return The group title or empty string if not found
	 */
    private static String extractGroupTitle(String extinfLine) {
        if (extinfLine == null || extinfLine.isEmpty()) {
            return "";
        }
        
        Matcher matcher = GROUP_TITLE_REGEX.matcher(extinfLine);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

	/**
	 * Extracts channel name from EXTINF line
	 * @param extinfLine The EXTINF line to parse
	 * @return The channel name or "Unknown" if not found
	 */
    private static String extractChannelName(String extinfLine) {
        if (extinfLine == null || extinfLine.isEmpty()) {
            return UNKNOWN_CHANNEL_NAME;
        }
        
        int comma = extinfLine.indexOf(',');
        if (comma != -1 && comma < extinfLine.length() - 1) {
            return extinfLine.substring(comma + 1).trim();
        }
        return UNKNOWN_CHANNEL_NAME;
    }

	/**
	 * Extracts tvg-id from EXTINF line
	 * @param extinfLine The EXTINF line to parse
	 * @return The tvg-id or empty string if not found
	 */
    private static String extractTvgId(String extinfLine) {
        if (extinfLine == null || extinfLine.isEmpty()) {
            return "";
        }
        
        Matcher matcher = TVG_ID_REGEX.matcher(extinfLine);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

	/**
	 * Extracts tvg-name from EXTINF line
	 * @param extinfLine The EXTINF line to parse
	 * @return The tvg-name or empty string if not found
	 */
    private static String extractTvgName(String extinfLine) {
        if (extinfLine == null || extinfLine.isEmpty()) {
            return "";
        }
        
        Matcher matcher = TVG_NAME_REGEX.matcher(extinfLine);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private static String extractTvgLogo(String extinfLine) {
        if (extinfLine == null || extinfLine.isEmpty()) {
            return "";
        }
        Matcher matcher = TVG_LOGO_REGEX.matcher(extinfLine);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
}
