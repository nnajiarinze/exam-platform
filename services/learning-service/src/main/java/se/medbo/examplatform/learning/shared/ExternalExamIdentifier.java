package se.medbo.examplatform.learning.shared;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** Canonical identifier used at every Learning Service exam boundary. */
public final class ExternalExamIdentifier {
    private static final Pattern CANONICAL = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private ExternalExamIdentifier() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) throw invalid();
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (!CANONICAL.matcher(normalized).matches()) throw invalid();
        return normalized;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EXAM_IDENTIFIER",
                "Exam identifier must use letters or numbers and normalize to lowercase kebab-case");
    }
}
