package se.eskimos.recorder;
import java.util.List;

/**
 * Helper class for paginating and displaying channel lists.
 */
public class ChannelListPager {
    /**
     * Displays a page of channels to the user.
     * @param channels List of channels
     * @param startIndex Index to start displaying from
     * @param out Output stream
     * @return Index of next page start, or -1 if last page
     */
    public static int displayPage(List<M3UHolder> channels, int startIndex, java.io.PrintStream out) {
        int endIndex = Math.min(startIndex + StringHelper.CHANNELS_PER_PAGE, channels.size());
        int maxNameLength = StringHelper.getMaxChannelNameLength(channels);
        StringHelper.printChannelList(channels, startIndex, endIndex, out::println, maxNameLength);
        return endIndex;
    }
} 