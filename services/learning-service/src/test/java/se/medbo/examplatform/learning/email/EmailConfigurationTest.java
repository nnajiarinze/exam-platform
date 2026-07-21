package se.medbo.examplatform.learning.email;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EmailConfigurationTest {
    @Test void localAndTestProviderNeverConstructResendClient(){
        var sender=new EmailConfiguration().transactionalEmailSender(new ObjectMapper(),new SimpleMeterRegistry(),Clock.systemUTC(),"logging","","","Svea Study","","https://api.resend.com",Duration.ofSeconds(1),0);
        assertThat(sender).isInstanceOf(LoggingEmailSender.class);
    }
}
