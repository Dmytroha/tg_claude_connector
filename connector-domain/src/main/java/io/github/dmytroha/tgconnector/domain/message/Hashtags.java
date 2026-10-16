package io.github.dmytroha.tgconnector.domain.message;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hashtags are how channels label their posts ({@code #склад}, {@code #реклама}), so they work as categories.
 * Stored and compared without the {@code #}, lower-cased.
 */
public final class Hashtags {

    private static final Pattern HASHTAG = Pattern.compile("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_]+)");

    private Hashtags() {
    }

    /** Distinct hashtags in order of first appearance. */
    public static Set<String> extract(String text) {
        var result = new LinkedHashSet<String>();
        if (text != null) {
            var matcher = HASHTAG.matcher(text);
            while (matcher.find()) {
                result.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** Normalizes user input: {@code "#Реклама"}, {@code " реклама "} → {@code "реклама"}; blanks dropped. */
    public static Set<String> normalize(Collection<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.strip().replaceFirst("^#+", "").toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
