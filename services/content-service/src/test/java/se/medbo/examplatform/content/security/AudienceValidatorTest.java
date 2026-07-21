package se.medbo.examplatform.content.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {
    private final AudienceValidator validator=new AudienceValidator("content-api");
    @Test void acceptsExpectedAudienceAndBearerType(){assertThat(validator.validate(jwt(List.of("content-api"),"Bearer")).hasErrors()).isFalse();}
    @Test void rejectsLearnerAudience(){assertThat(validator.validate(jwt(List.of("learning-api"),"Bearer")).hasErrors()).isTrue();}
    @Test void rejectsWrongTokenType(){assertThat(validator.validate(jwt(List.of("content-api"),"Refresh")).hasErrors()).isTrue();}
    private Jwt jwt(List<String> audience,String type){return Jwt.withTokenValue("token").header("alg","RS256").subject("admin").audience(audience).claim("typ",type).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();}
}
