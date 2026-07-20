package se.medbo.examplatform.content.question;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.content.shared.DomainException;

class QuestionValidatorTest {
    private final QuestionValidator validator = new QuestionValidator();
    @Test void acceptsSingleChoiceWithExactlyOneCorrectOption(){assertThatCode(()->validator.validate(input("SINGLE_CHOICE",List.of(option(0,"A",true),option(1,"B",false)),null))).doesNotThrowAnyException();}
    @Test void rejectsSingleChoiceWithMultipleCorrectOptions(){assertThatThrownBy(()->validator.validate(input("SINGLE_CHOICE",List.of(option(0,"A",true),option(1,"B",true)),null))).isInstanceOf(DomainException.class).hasMessageContaining("exactly one");}
    @Test void rejectsDuplicateOptionTextIgnoringCase(){assertThatThrownBy(()->validator.validate(input("MULTIPLE_CHOICE",List.of(option(0,"Same",true),option(1," same ",false)),null))).isInstanceOf(DomainException.class).hasMessageContaining("Duplicate");}
    @Test void trueFalseRequiresItsCorrectAnswer(){assertThatThrownBy(()->validator.validate(input("TRUE_FALSE",List.of(),null))).isInstanceOf(DomainException.class).hasMessageContaining("trueFalseCorrect");}
    private QuestionService.QuestionInput input(String type,List<QuestionService.OptionInput> options,Boolean tf){return new QuestionService.QuestionInput(UUID.randomUUID(),"Q-1",type,"Question?","EASY",null,List.of(UUID.randomUUID()),List.of(),options,tf,null);}
    private QuestionService.OptionInput option(int order,String text,boolean correct){return new QuestionService.OptionInput(null,order,text,correct,null);}
}
