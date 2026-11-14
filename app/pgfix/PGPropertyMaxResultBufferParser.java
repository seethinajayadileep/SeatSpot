package org.postgresql.util;

/**
 * Android build of the pgjdbc parser. The upstream class always calls
 * java.lang.management.ManagementFactory, which Android does not provide.
 */
public class PGPropertyMaxResultBufferParser {

    public static long parseProperty(String value) throws PSQLException {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        if (endsWithPercent(value)) {
            throw new PSQLException(
                    GT.tr("Percent maxResultBuffer is not supported on Android. Value: {0}", value),
                    PSQLState.SYNTAX_ERROR);
        }
        return parseByteValue(value);
    }

    private static boolean endsWithPercent(String value) {
        return value.endsWith("percent") || value.endsWith("pct") || value.endsWith("p") || value.endsWith("%");
    }

    private static long parseByteValue(String value) throws PSQLException {
        long multiplier = 1L;
        char sign = value.charAt(value.length() - 1);
        String number = value;
        switch (sign) {
            case 'T':
            case 't':
                multiplier *= 1000L;
                // fall through
            case 'G':
            case 'g':
                multiplier *= 1000L;
                // fall through
            case 'M':
            case 'm':
                multiplier *= 1000L;
                // fall through
            case 'K':
            case 'k':
                multiplier *= 1000L;
                number = value.substring(0, value.length() - 1);
                break;
            default:
                if (sign < '0' || sign > '9') {
                    throw new PSQLException(
                            GT.tr("Received MaxResultBuffer parameter can't be parsed. Value received to parse: {0}", value),
                            PSQLState.SYNTAX_ERROR);
                }
                break;
        }
        try {
            return Long.parseLong(number) * multiplier;
        } catch (NumberFormatException error) {
            throw new PSQLException(
                    GT.tr("Received MaxResultBuffer parameter can't be parsed. Value received to parse: {0}", value),
                    PSQLState.SYNTAX_ERROR, error);
        }
    }
}
