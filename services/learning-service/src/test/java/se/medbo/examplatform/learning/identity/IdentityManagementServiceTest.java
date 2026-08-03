package se.medbo.examplatform.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityManagementServiceTest {
    @Test void countsPasswordAndDistinctProviderLinksAsUsableLoginMethods() {
        assertThat(IdentityManagementService.countUsableMethods(new KeycloakIdentityAdminClient.Methods(true, List.of("google", "google", "apple"))))
                .isEqualTo(3);
    }

    @Test void providerOnlyAccountHasExactlyOneUsableMethod() {
        assertThat(IdentityManagementService.countUsableMethods(new KeycloakIdentityAdminClient.Methods(false, List.of("apple"))))
                .isEqualTo(1);
    }
}
