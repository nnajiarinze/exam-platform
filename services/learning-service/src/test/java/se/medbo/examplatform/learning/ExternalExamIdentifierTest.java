package se.medbo.examplatform.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.learning.shared.ApiException;
import se.medbo.examplatform.learning.shared.ExternalExamIdentifier;

class ExternalExamIdentifierTest {
    @Test void normalizesSupportedRepresentations(){
        assertThat(ExternalExamIdentifier.normalize("SWEDISH_CITIZENSHIP")).isEqualTo("swedish-citizenship");
        assertThat(ExternalExamIdentifier.normalize("Swedish Citizenship")).isEqualTo("swedish-citizenship");
        assertThat(ExternalExamIdentifier.normalize("swedish-citizenship")).isEqualTo("swedish-citizenship");
    }
    @Test void rejectsBlankOrInvalidRepresentations(){
        assertThatThrownBy(()->ExternalExamIdentifier.normalize(" ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->ExternalExamIdentifier.normalize("___")).isInstanceOf(ApiException.class);
    }
}
