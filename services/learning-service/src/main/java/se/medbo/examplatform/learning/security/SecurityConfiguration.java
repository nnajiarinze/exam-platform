package se.medbo.examplatform.learning.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class SecurityConfiguration {
    @Bean SecurityFilterChain learningSecurity(HttpSecurity http,DevelopmentLearnerAuthenticationFilter development,ObjectMapper mapper)throws Exception{
        return http.csrf(csrf->csrf.disable()).cors(Customizer.withDefaults()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/health/**","/internal/v1/**").permitAll().requestMatchers("/api/v1/content/**","/api/v1/me","/api/v1/me/**","/api/v1/practice-sessions","/api/v1/practice-sessions/**","/api/v1/mock-exams","/api/v1/mock-exams/**","/api/v1/progress","/api/v1/progress/**").hasRole("LEARNER").anyRequest().permitAll())
            .oauth2ResourceServer(o->o.jwt(jwt->jwt.jwtAuthenticationConverter(learnerJwtConverter())).authenticationEntryPoint((req,res,error)->write(res,mapper,401,"AUTHENTICATION_REQUIRED","Valid learner authentication is required")))
            .exceptionHandling(e->e.accessDeniedHandler((req,res,error)->write(res,mapper,403,"ACCESS_DENIED","Learner access is forbidden")))
            .addFilterBefore(development,UsernamePasswordAuthenticationFilter.class).build();
    }
    @Bean NimbusJwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")String issuer,@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")String jwks,@Value("${learning.security.audience:learning-api}")String audience){
        var decoder=NimbusJwtDecoder.withJwkSetUri(jwks).build();decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),new AudienceValidator(audience)));return decoder;
    }
    private static JwtAuthenticationConverter learnerJwtConverter(){
        var converter=new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(jwt->{
            Object access=jwt.getClaim("realm_access");
            if(!(access instanceof java.util.Map<?,?> map)||!(map.get("roles") instanceof java.util.Collection<?> roles)||roles.stream().map(Object::toString).noneMatch("LEARNER"::equals))return List.of();
            return List.of(new SimpleGrantedAuthority("ROLE_LEARNER"));
        });
        return converter;
    }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${learning.identity.allowed-origins:http://localhost:*,http://127.0.0.1:*}")List<String> origins){var c=new CorsConfiguration();c.setAllowedOriginPatterns(origins);c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Authorization","Content-Type","X-Learner-Identity","X-Internal-Api-Key"));var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",c);return source;}
    private static void write(HttpServletResponse response,ObjectMapper mapper,int status,String code,String message)throws java.io.IOException{response.setStatus(status);response.setContentType(MediaType.APPLICATION_JSON_VALUE);mapper.writeValue(response.getOutputStream(),java.util.Map.of("code",code,"message",message,"errors",List.of()));}
}
