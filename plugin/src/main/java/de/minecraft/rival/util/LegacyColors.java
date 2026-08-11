package de.minecraft.rival.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic legacy/hex conversion shared by every Bukkit text surface. */
public final class LegacyColors {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String CODES = "0123456789abcdefklmnorx";

    private LegacyColors() {}

    public static String translate(String value) {
        String source = value == null ? "" : value;
        Matcher matcher = HEX.matcher(source);
        StringBuffer converted = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char digit : hex.toCharArray()) replacement.append('§').append(digit);
            matcher.appendReplacement(converted, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(converted);

        char[] output = converted.toString().toCharArray();
        for (int i = 0; i + 1 < output.length; i++) {
            if (output[i] != '&') continue;
            char code = Character.toLowerCase(output[i + 1]);
            if (CODES.indexOf(code) < 0) continue;
            output[i] = '§';
            output[i + 1] = code;
        }
        return new String(output);
    }
}
