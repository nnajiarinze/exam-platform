package se.medbo.examplatform.content.security;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

final class AudienceValidator implements OAuth2TokenValidator<Jwt>{
    private final String audience;AudienceValidator(String audience){this.audience=audience;}
    @Override public OAuth2TokenValidatorResult validate(Jwt jwt){return jwt.getAudience().contains(audience)&&"Bearer".equalsIgnoreCase(jwt.getClaimAsString("typ"))?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required audience or bearer token type is missing",null));}
}
