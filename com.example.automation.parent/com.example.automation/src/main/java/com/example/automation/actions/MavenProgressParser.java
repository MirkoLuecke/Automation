package com.example.automation.actions;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenProgressParser {

    private static final Pattern NM = Pattern.compile("\\[(\\d+)/(\\d+)\\]");

    private int lastN = 0;
    private int lastM = 0;
    private boolean seenNM = false;

    public OptionalInt parse(String line) {
        if (line == null) return OptionalInt.empty();

        Matcher m = NM.matcher(line);
        if (m.find()) {
            lastN = Integer.parseInt(m.group(1));
            lastM = Integer.parseInt(m.group(2));
            seenNM = true;
            if (lastM > 0) return OptionalInt.of((lastN - 1) * 100 / lastM);
        }

        if (line.contains("BUILD SUCCESS")) {
            if (seenNM && lastM > 0) return OptionalInt.of(lastN * 100 / lastM);
            return OptionalInt.of(100);
        }
        if (line.contains("BUILD FAILURE")) return OptionalInt.empty();

        if (!line.contains("[INFO] ---")) return OptionalInt.empty();
        if (line.contains(":deploy"))       return OptionalInt.of(95);
        if (line.contains(":install"))      return OptionalInt.of(90);
        if (line.contains(":jar") || line.contains(":war") || line.contains(":ear"))
                                            return OptionalInt.of(75);
        if (line.contains(":testCompile"))  return OptionalInt.of(45);
        if (line.contains(":test"))         return OptionalInt.of(60);
        if (line.contains(":compile"))      return OptionalInt.of(30);
        if (line.contains(":resources"))    return OptionalInt.of(15);

        return OptionalInt.empty();
    }
}
