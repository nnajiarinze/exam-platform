package se.medbo.examplatform.learning.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.Flow;

class ResendEmailSenderTest {
    private final HttpClient client=mock(HttpClient.class);private final ObjectMapper mapper=new ObjectMapper();
    private ResendEmailSender sender(int retries){return new ResendEmailSender(client,mapper,Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"),ZoneOffset.UTC),new SimpleMeterRegistry(),"https://api.resend.com","secret-test-key","noreply@example.test","Svea Study","support@example.test",Duration.ofSeconds(2),retries);}
    private TransactionalEmail email(){return new TransactionalEmail("learner@example.test","Verify account","Plain text","<p>HTML</p>","verification");}
    @SuppressWarnings("unchecked") private HttpResponse<String> response(int status,String body)throws Exception{var response=(HttpResponse<String>)mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body);return response;}

    @Test void mapsSenderReplyToTextHtmlAndCapturesMessageId()throws Exception{
        var accepted=response(200,"{\"id\":\"resend-message-1\"}");when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(accepted);
        assertThat(sender(0).send(email()).providerMessageId()).isEqualTo("resend-message-1");
        var request=ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(request.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().headers().firstValue("Authorization")).hasValue("Bearer secret-test-key");
        var payload=mapper.readTree(body(request.getValue()));
        assertThat(payload.path("from").asText()).isEqualTo("Svea Study <noreply@example.test>");
        assertThat(payload.path("reply_to").asText()).isEqualTo("support@example.test");
        assertThat(payload.path("text").asText()).isEqualTo("Plain text");
        assertThat(payload.path("html").asText()).isEqualTo("<p>HTML</p>");
    }
    @Test void retriesTransientFailures()throws Exception{
        var unavailable=response(503,"{}");var accepted=response(200,"{\"id\":\"ok\"}");when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(unavailable,accepted);
        assertThat(sender(1).send(email()).providerMessageId()).isEqualTo("ok");verify(client,times(2)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test void doesNotRetryPermanentFailures()throws Exception{
        var rejected=response(422,"{}");when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(rejected);
        assertThatThrownBy(()->sender(2).send(email())).isInstanceOf(ResendEmailSender.EmailDeliveryException.class);verify(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test void reportsTimeoutAsTransientAfterBoundedRetries()throws Exception{
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timed out"));
        assertThatThrownBy(()->sender(1).send(email())).isInstanceOf(ResendEmailSender.EmailDeliveryException.class).satisfies(error->assertThat(((ResendEmailSender.EmailDeliveryException)error).isTransientFailure()).isTrue());
        verify(client,times(2)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test void requiresConfigurationAndRejectsInvalidRecipients(){
        assertThatThrownBy(()->new ResendEmailSender(client,mapper,Clock.systemUTC(),new SimpleMeterRegistry(),"https://api.resend.com","","from@example.test","Svea","",Duration.ofSeconds(1),0)).isInstanceOf(IllegalStateException.class).hasMessageContaining("RESEND_API_KEY");
        assertThatThrownBy(()->new TransactionalEmail("invalid","subject","text","<p>html</p>","key")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void escapesTemplateVariables(){assertThat(AuthenticationEmailTemplate.verification("a@example.test","<Admin>","https://example.test/?a=1&b=2").htmlBody()).contains("&lt;Admin&gt;").contains("a=1&amp;b=2");}
    private static String body(HttpRequest request){var output=new ByteArrayOutputStream();request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>(){public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}public void onNext(ByteBuffer item){var bytes=new byte[item.remaining()];item.get(bytes);output.writeBytes(bytes);}public void onError(Throwable throwable){throw new AssertionError(throwable);}public void onComplete(){}});return output.toString(StandardCharsets.UTF_8);}
}
