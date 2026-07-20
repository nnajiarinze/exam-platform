package se.medbo.examplatform.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.content.shared.ExternalExamIdentifier;

class ExternalExamIdentifierTest {
    @Test void normalizesSupportedRepresentations(){
        assertThat(ExternalExamIdentifier.normalize("SWEDISH_CITIZENSHIP")).isEqualTo("swedish-citizenship");
        assertThat(ExternalExamIdentifier.normalize("Swedish Citizenship")).isEqualTo("swedish-citizenship");
        assertThat(ExternalExamIdentifier.normalize("swedish-citizenship")).isEqualTo("swedish-citizenship");
    }
    @Test void rejectsBlankOrInvalidRepresentations(){
        assertThatThrownBy(()->ExternalExamIdentifier.normalize(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ExternalExamIdentifier.normalize("___")).isInstanceOf(IllegalArgumentException.class);
    }
}
