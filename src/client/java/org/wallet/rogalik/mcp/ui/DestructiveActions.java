package org.wallet.rogalik.mcp.ui;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognises menu actions that destroy data.
 *
 * <p>Clicking blind through menus is only safe if "Delete World" is not one click away from
 * "Edit". These matches are refused unless the caller explicitly asks for a destructive click
 * and the config allows it.
 */
public final class DestructiveActions {

    private static final List<Pattern> PATTERNS = List.of(
        // English
        Pattern.compile("\\bdelete\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\berase\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bwipe\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bremove\\s+(world|pack|server|realm|save)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\breset\\s+(world|defaults|to\\s+default)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bre-?create\\s+world\\b", Pattern.CASE_INSENSITIVE),
        // Russian. CASE_INSENSITIVE alone only folds ASCII, so UNICODE_CASE is required for
        // a capitalised Cyrillic label such as "Удалить мир" to match.
        Pattern.compile("удал", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("сброс", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("стереть", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    );

    private DestructiveActions() {
    }

    /** The reason a label is considered destructive, or empty when it is safe. */
    public static Optional<String> reasonFor(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        String normalized = label.trim();
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return Optional.of("the label '" + normalized.toLowerCase(Locale.ROOT)
                    + "' looks like it destroys data");
            }
        }
        return Optional.empty();
    }

    public static boolean isDestructive(String label) {
        return reasonFor(label).isPresent();
    }
}
