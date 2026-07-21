package se.medbo.examplatform.learning.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EmailConfiguration {
    @Bean Clock emailClock(){return Clock.systemUTC();}
    @Bean TransactionalEmailSender transactionalEmailSender(ObjectMapper mapper,MeterRegistry metrics,Clock clock,
            @Value("${email.provider:logging}")String provider,@Value("${email.resend.api-key:}")String apiKey,
            @Value("${email.resend.from-email:}")String fromEmail,@Value("${email.resend.from-name:Svea Study}")String fromName,
            @Value("${email.resend.reply-to-email:}")String replyTo,@Value("${email.resend.api-base-url:https://api.resend.com}")String baseUrl,
            @Value("${email.resend.timeout:10s}")Duration timeout,@Value("${email.resend.max-retries:2}")int maxRetries){
        if("logging".equalsIgnoreCase(provider)||"fake".equalsIgnoreCase(provider))return new LoggingEmailSender(clock);
        if(!"resend".equalsIgnoreCase(provider))throw new IllegalStateException("Unsupported EMAIL_PROVIDER: "+provider);
        return new ResendEmailSender(HttpClient.newBuilder().connectTimeout(timeout).build(),mapper,clock,metrics,baseUrl,apiKey,fromEmail,fromName,replyTo,timeout,maxRetries);
    }
}
