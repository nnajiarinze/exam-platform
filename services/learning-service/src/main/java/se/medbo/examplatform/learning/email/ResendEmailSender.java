package se.medbo.examplatform.learning.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ResendEmailSender implements TransactionalEmailSender {
    private static final Logger LOG = LoggerFactory.getLogger(ResendEmailSender.class);
    private final HttpClient client; private final ObjectMapper mapper; private final Clock clock;
    private final MeterRegistry metrics; private final URI endpoint; private final String apiKey, from, replyTo; private final Duration timeout; private final int maxRetries;

    ResendEmailSender(HttpClient client, ObjectMapper mapper, Clock clock, MeterRegistry metrics, String baseUrl,
            String apiKey, String fromEmail, String fromName, String replyTo, Duration timeout, int maxRetries) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("RESEND_API_KEY is required when EMAIL_PROVIDER=resend");
        if (fromEmail == null || fromEmail.isBlank()) throw new IllegalStateException("RESEND_FROM_EMAIL is required when EMAIL_PROVIDER=resend");
        URI base = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(base.getScheme())) throw new IllegalArgumentException("Resend API base URL must use HTTPS");
        this.client=client;this.mapper=mapper;this.clock=clock;this.metrics=metrics;this.endpoint=base.resolve("/emails");this.apiKey=apiKey;
        this.from=(fromName==null||fromName.isBlank())?fromEmail:fromName+" <"+fromEmail+">";this.replyTo=replyTo;this.timeout=timeout;this.maxRetries=Math.max(0,Math.min(maxRetries,3));
    }

    @Override public EmailDelivery send(TransactionalEmail email) {
        var requestedAt=clock.instant();var idempotencyKey="email/"+UUID.randomUUID();var payload=new LinkedHashMap<String,Object>();payload.put("from",from);payload.put("to",new String[]{email.recipient()});payload.put("subject",email.subject());payload.put("text",email.textBody());payload.put("html",email.htmlBody());if(replyTo!=null&&!replyTo.isBlank())payload.put("reply_to",replyTo);
        for(int attempt=0;;attempt++) try {
            var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Authorization","Bearer "+apiKey).header("User-Agent","SveaStudy/1.0").header("Idempotency-Key",idempotencyKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload),StandardCharsets.UTF_8)).build();
            var started=System.nanoTime();var response=client.send(request,HttpResponse.BodyHandlers.ofString());metrics.timer("email.provider.latency","provider","resend").record(Duration.ofNanos(System.nanoTime()-started));
            if(response.statusCode()/100==2){String id=mapper.readTree(response.body()).path("id").asText();if(id.isBlank())throw new EmailDeliveryException("Resend response did not include a message id",false);metrics.counter("email.send","provider","resend","outcome","success").increment();LOG.info("Transactional email accepted provider=resend template={} message_id={}",email.templateKey(),id);return new EmailDelivery("resend",id,requestedAt);}
            boolean transientFailure=response.statusCode()==429||response.statusCode()>=500;
            if(transientFailure&&attempt<maxRetries)continue;
            metrics.counter("email.send","provider","resend","outcome","failure").increment();throw new EmailDeliveryException("Resend rejected the message with status "+response.statusCode(),transientFailure);
        } catch(EmailDeliveryException exception){throw exception;} catch(InterruptedException exception){Thread.currentThread().interrupt();throw new EmailDeliveryException("Email delivery was interrupted",true);} catch(Exception exception){if(attempt<maxRetries)continue;metrics.counter("email.send","provider","resend","outcome","failure").increment();throw new EmailDeliveryException("Resend could not be reached",true);}
    }

    static final class EmailDeliveryException extends RuntimeException { private final boolean transientFailure; EmailDeliveryException(String message,boolean transientFailure){super(message);this.transientFailure=transientFailure;} boolean isTransientFailure(){return transientFailure;} }
}
