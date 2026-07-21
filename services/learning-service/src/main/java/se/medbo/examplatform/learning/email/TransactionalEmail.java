package se.medbo.examplatform.learning.email;

import java.util.regex.Pattern;

public record TransactionalEmail(String recipient, String subject, String textBody, String htmlBody, String templateKey) {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    public TransactionalEmail {
        if (recipient == null || recipient.length() > 320 || !EMAIL.matcher(recipient).matches()) throw new IllegalArgumentException("A valid recipient is required");
        if (subject == null || subject.isBlank() || subject.length() > 200) throw new IllegalArgumentException("Subject must contain 1 to 200 characters");
        if (textBody == null || textBody.isBlank() || textBody.length() > 100_000) throw new IllegalArgumentException("A bounded plain-text body is required");
        if (htmlBody == null || htmlBody.isBlank() || htmlBody.length() > 200_000) throw new IllegalArgumentException("A bounded HTML body is required");
        if (templateKey == null || templateKey.isBlank() || templateKey.length() > 100) throw new IllegalArgumentException("A bounded template key is required");
    }
}
