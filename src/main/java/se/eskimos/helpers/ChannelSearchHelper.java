package se.eskimos.helpers;
import java.util.List;
import java.util.ArrayList;
import se.eskimos.m3u.M3UHolder;

/**
 * Helper class for searching and filtering channels (M3UHolder).
 */
public class ChannelSearchHelper {
    /**
     * Returns a list of channels whose name or tvgName contains the search string (case-insensitive).
     * @param channels List of channels to search
     * @param search Search string
     * @return List of matching channels
     */
    public static List<M3UHolder> searchChannels(List<M3UHolder> channels, String search) {
        List<M3UHolder> matches = new ArrayList<>();
        String[] searchWords = search.trim().toLowerCase().split("\\s+");
        for (M3UHolder ch : channels) {
            String name = ch.tvgName() != null && !ch.tvgName().isEmpty() ? ch.tvgName() : ch.name();
            if (name != null) {
                String nameLower = name.toLowerCase();
                boolean allMatch = true;
                for (String word : searchWords) {
                    if (!nameLower.contains(word)) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    matches.add(ch);
                }
            }
        }
        return matches;
    }
} 