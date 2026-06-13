package com.example.automation.actions;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Maven console output lines and converts them to a 0–100 progress percentage.
 * Recognises {@code [N/M]} module-count markers, phase-name markers in
 * {@code [INFO] ---} lines, and {@code BUILD SUCCESS}.
 */
public class MavenProgressParser {

    private static final Pattern NM = Pattern.compile("\\[(\\d+)/(\\d+)\\]");

    private int currentN = 0;
    private int totalM = 0;

    /**
     * Parses one line of Maven output and returns the estimated progress percentage.
     *
     * @param line a single line from Maven stdout; may be null
     * @return the estimated percentage (0–100), or empty if the line carries no progress signal
     */
    public OptionalInt parse(String line) {
        if (line == null) return OptionalInt.empty();

        Matcher m = NM.matcher(line);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            int total = Integer.parseInt(m.group(2));
            if (total > 0) {
                currentN = n;
                totalM = total;
                return OptionalInt.of((n - 1) * 100 / total);
            }
        }

        if (line.contains("BUILD SUCCESS")) return OptionalInt.of(100);
        if (line.contains("BUILD FAILURE")) return OptionalInt.empty();

        if (!line.contains("[INFO] ---")) return OptionalInt.empty();

        int phase;
        if      (line.contains(":deploy"))      phase = 95;
        else if (line.contains(":install"))     phase = 90;
        else if (line.contains(":jar") || line.contains(":war") || line.contains(":ear"))
                                                phase = 75;
        else if (line.contains(":testCompile")) phase = 45;
        else if (line.contains(":test"))        phase = 60;
        else if (line.contains(":compile"))     phase = 30;
        else if (line.contains(":resources"))   phase = 15;
        else return OptionalInt.empty();

        // When module count is known, blend module index with phase so progress
        // advances across all modules instead of resetting each time.
        if (totalM > 0) {
            int base = (currentN - 1) * 100 / totalM;
            int slot = 100 / totalM;
            return OptionalInt.of(base + phase * slot / 100);
        }
        return OptionalInt.of(phase);
    }
}
