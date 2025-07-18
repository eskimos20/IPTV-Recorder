package se.eskimos.helpers;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.format.SignStyle;

/**
 * Helper for flexible date/time parsing and formatting.
 */
public class DateTimeHelper {
    /**
     * Parses a time string using the provided formatter, falling back to a flexible formatter that accepts 1 or 2 digit hours.
     * @param time The time string (e.g. '8:30' or '08:30')
     * @param primaryFormatter The main formatter to try first
     * @return LocalTime parsed from the string
     * @throws DateTimeParseException if parsing fails
     */
    public static LocalTime parseFlexibleLocalTime(String time, DateTimeFormatter primaryFormatter) {
        try {
            return LocalTime.parse(time, primaryFormatter);
        } catch (DateTimeParseException e) {
            DateTimeFormatter flexibleFormatter = new DateTimeFormatterBuilder()
                .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NOT_NEGATIVE)
                .appendLiteral(':')
                .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                .toFormatter();
            return LocalTime.parse(time, flexibleFormatter);
        }
    }
} 