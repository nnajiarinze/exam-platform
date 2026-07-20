package se.medbo.examplatform.content.review;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.content.shared.DomainException;

class ReviewWorkflowServiceTest {
    private final ReviewWorkflowService service=new ReviewWorkflowService(null);
    @Test void returnDecisionRequiresStructuredOrDescriptiveFeedback(){assertThatThrownBy(()->service.validateFeedback("REQUIRES_UPDATE",null,"short")).isInstanceOf(DomainException.class).hasMessageContaining("reason code");}
    @Test void otherReasonRequiresComment(){assertThatThrownBy(()->service.validateFeedback("REJECTED","OTHER",null)).isInstanceOf(DomainException.class).hasMessageContaining("comment");}
    @Test void knownReasonAndCommentAreAccepted(){assertThatCode(()->service.validateFeedback("REJECTED","AMBIGUOUS_WORDING","Clarify the wording." )).doesNotThrowAnyException();}
    @Test void unknownReasonIsRejected(){assertThatThrownBy(()->service.validateFeedback("REJECTED","NOT_A_REASON","Detailed feedback here.")).isInstanceOf(DomainException.class).hasMessageContaining("Unsupported");}
}
