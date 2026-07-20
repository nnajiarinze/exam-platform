package se.medbo.examplatform.content.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical cross-service exam identifier; internal editorial codes remain unchanged. */
public final class ExternalExamIdentifier {
    private static final Pattern CANONICAL = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private ExternalExamIdentifier() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Exam identifier is required");
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (!CANONICAL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Exam identifier must contain letters or numbers");
        }
        return normalized;
    }
}
