package se.medbo.examplatform.learning.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {
    private final AudienceValidator validator=new AudienceValidator("learning-api");
    @Test void acceptsExpectedAudienceAndBearerType(){assertThat(validator.validate(jwt(List.of("learning-api"),"Bearer")).hasErrors()).isFalse();}
    @Test void rejectsWrongAudience(){assertThat(validator.validate(jwt(List.of("content-api"),"Bearer")).hasErrors()).isTrue();}
    @Test void rejectsWrongTokenType(){assertThat(validator.validate(jwt(List.of("learning-api"),"Refresh")).hasErrors()).isTrue();}
    private Jwt jwt(List<String> audience,String type){return Jwt.withTokenValue("token").header("alg","RS256").subject("learner").audience(audience).claim("typ",type).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();}
}
