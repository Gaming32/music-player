package io.github.gaming32.musicplayer;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class SharedSuggestionProvider {
    public static CompletableFuture<Suggestions> suggest(Stream<String> candidates, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);
        candidates.filter(candidate -> matchesSubStr(string, candidate.toLowerCase(Locale.ROOT))).forEach(builder::suggest);
        return builder.buildFuture();
    }

    public static boolean matchesSubStr(String remaining, String candidate) {
        for (int i = 0; !candidate.startsWith(remaining, i); ++i) {
            i = candidate.indexOf(95, i);
            if (i < 0) {
                return false;
            }
        }

        return true;
    }
}
