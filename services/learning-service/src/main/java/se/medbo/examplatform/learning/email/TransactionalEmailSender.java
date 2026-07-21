package se.medbo.examplatform.learning.email;

public interface TransactionalEmailSender {
    EmailDelivery send(TransactionalEmail email);
}
