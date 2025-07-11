package se.eskimos.recorder;
import java.util.List;

/**
 * Helper class for paginating and displaying channel lists.
 */
public class ChannelListPager {
    private static final int CHANNELS_PER_PAGE = 20;

    /**
     * Displays a page of channels to the user.
     * @param channels List of channels
     * @param startIndex Index to start displaying from
     * @param out Output stream
     * @return Index of next page start, or -1 if last page
     */
    public static int displayPage(List<M3UHolder> channels, int startIndex, java.io.PrintStream out) {
        int counter = 0;
        int totalChannels = channels.size();
        for (int i = startIndex; i < totalChannels && counter < CHANNELS_PER_PAGE; i++) {
            M3UHolder mH = channels.get(i);
            String displayName = HelpText.CHANNEL_NAME_PREFIX + (mH.tvgName() != null && !mH.tvgName().isEmpty() ? mH.tvgName().trim() : mH.name().trim());
            String displayCode = HelpText.CHANNEL_CODE_PREFIX + mH.code().trim();
            int lengthTotal = displayName.length() + displayCode.length();
            String underLine = new String(new char[lengthTotal]).replace('\0', '_');
            out.println(displayName + " " + displayCode);
            out.println(underLine);
            counter++;
        }
        if (startIndex + CHANNELS_PER_PAGE < totalChannels) {
            return startIndex + CHANNELS_PER_PAGE;
        } else {
            return -1;
        }
    }
} 