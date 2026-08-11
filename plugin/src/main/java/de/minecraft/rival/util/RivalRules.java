package de.minecraft.rival.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RivalRules {
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([smhdw])");
    private RivalRules() {}

    public static boolean warningCrossed(long before, long after, long threshold) {
        return before > threshold && after <= threshold;
    }

    public static boolean allowedSide(int side, double coordinate, double split) {
        return side < 0 ? coordinate < split : side > 0 && coordinate > split;
    }

    public static String formatDuration(long seconds) {
        long safe = Math.max(0, seconds);
        return "%02d:%02d:%02d".formatted(safe / 3600, (safe % 3600) / 60, safe % 60);
    }

    public static boolean insideVerticalZone(double x, double z, double ax, double az, double bx, double bz) {
        return x >= Math.min(ax, bx) && x <= Math.max(ax, bx)
            && z >= Math.min(az, bz) && z <= Math.max(az, bz);
    }

    public static boolean verticalZonesOverlap(double a1x, double a1z, double a2x, double a2z,
                                               double b1x, double b1z, double b2x, double b2z) {
        return Math.max(Math.min(a1x, a2x), Math.min(b1x, b2x)) <= Math.min(Math.max(a1x, a2x), Math.max(b1x, b2x))
            && Math.max(Math.min(a1z, a2z), Math.min(b1z, b2z)) <= Math.min(Math.max(a1z, a2z), Math.max(b1z, b2z));
    }

    public static boolean canStartEndFight(int remainingPlayers, int onlineRemainingPlayers) {
        return remainingPlayers == 2 && onlineRemainingPlayers == 2;
    }

    public static long parseBanDurationMillis(String raw) {
        if (raw == null) throw new IllegalArgumentException("Dauer fehlt");
        String value = raw.toLowerCase(Locale.ROOT).strip();
        if (value.equals("permanent") || value.equals("perma") || value.equals("perm") || value.equals("immer")) return 0L;
        Matcher matcher = DURATION_PART.matcher(value);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("Ungültige Dauer");
            long amount = Long.parseLong(matcher.group(1));
            long factor = switch (matcher.group(2)) {
                case "s" -> 1_000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                case "w" -> 604_800_000L;
                default -> throw new IllegalArgumentException("Ungültige Einheit");
            };
            total = Math.addExact(total, Math.multiplyExact(amount, factor));
            end = matcher.end();
        }
        if (end != value.length() || total <= 0) throw new IllegalArgumentException("Ungültige Dauer");
        return total;
    }
}
