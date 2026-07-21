package se.medbo.examplatform.learning.email;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LoggingEmailSender implements TransactionalEmailSender {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingEmailSender.class);
    private final Clock clock;
    LoggingEmailSender(Clock clock) { this.clock = clock; }
    @Override public EmailDelivery send(TransactionalEmail email) {
        String domain = email.recipient().substring(email.recipient().lastIndexOf('@') + 1);
        LOG.info("Transactional email simulated provider=logging template={} recipient_domain={}", email.templateKey(), domain);
        return new EmailDelivery("logging", "local-" + UUID.randomUUID(), clock.instant());
    }
}
