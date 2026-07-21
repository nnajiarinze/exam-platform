package se.medbo.examplatform.ai.question;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import se.medbo.examplatform.ai.provider.AiProviderException;

@Component
final class QuestionProposalValidator {
  private static final Set<String> TYPES=Set.of("SINGLE_CHOICE","TRUE_FALSE","MULTIPLE_CHOICE");
  private static final Set<String> RESULTS=Set.of("QUESTIONS_PROPOSED","INSUFFICIENT_GROUNDED_INFORMATION","FACT_NOT_SUITABLE_FOR_QUESTION");
  private static final Pattern HTML=Pattern.compile("<[^>]+>");
  private static final Pattern INJECTION=Pattern.compile("(?i)(ignore (all|any|the)? ?previous instructions|system prompt|publish (this|the) question|call another service|use information from the internet)");

  List<QuestionGenerationProviderClient.Proposal> validate(QuestionGenerationProviderClient.Request request,
      QuestionGenerationProviderClient.Result result) {
    if(result==null||!RESULTS.contains(result.resultType())) fail("AI_QUESTION_GENERATION_RESULT_TYPE_INVALID","Provider returned an unsupported result type");
    List<QuestionGenerationProviderClient.Proposal> proposals=result.proposals()==null?List.of():result.proposals();
    if(!"QUESTIONS_PROPOSED".equals(result.resultType())) {
      if(!proposals.isEmpty()) fail("AI_QUESTION_GENERATION_OUTPUT_INVALID","A no-generation result cannot contain proposals");
      return List.of();
    }
    if(proposals.isEmpty()||proposals.size()>request.proposalCount()) fail("AI_QUESTION_GENERATION_OUTPUT_INVALID","Provider returned an invalid proposal count");
    var seen=new HashSet<String>();
    for(var proposal:proposals){validateProposal(request,proposal);if(!seen.add(normalize(proposal.questionText())))fail("AI_QUESTION_GENERATION_DUPLICATE_PROPOSAL","Provider returned duplicate proposals");}
    return proposals;
  }

  void validateProposal(QuestionGenerationProviderClient.Request request,QuestionGenerationProviderClient.Proposal p){
    required(p.questionText(),"question text");required(p.explanation(),"explanation");required(p.rationale(),"rationale");
    if(HTML.matcher(p.questionText()).find()||INJECTION.matcher(p.questionText()).find()||p.questionText().length()>1000)fail("AI_QUESTION_GENERATION_OUTPUT_INVALID","Question text is unsafe or invalid");
    if(!TYPES.contains(p.questionType())||(request.questionType()!=null&&!request.questionType().equals(p.questionType())))fail("AI_QUESTION_GENERATION_QUESTION_TYPE_INVALID","Provider returned an unsupported question type");
    if(!request.target().language().equalsIgnoreCase(p.language()))fail("AI_QUESTION_GENERATION_LANGUAGE_MISMATCH","Provider returned the wrong language");
    var fact=p.factEvidence();if(fact==null||!request.target().knowledgeFactId().equals(fact.knowledgeFactId())||request.target().version()!=fact.knowledgeFactVersion()||!request.target().checksum().equals(fact.knowledgeFactChecksum()))fail("AI_QUESTION_GENERATION_GROUNDING_MISMATCH","Proposal does not reference the immutable target fact");
    required(fact.supportedClaim(),"supported claim");
    if(!plausiblyRelated(fact.supportedClaim(),request.target().text()))fail("AI_QUESTION_GENERATION_UNSUPPORTED_CLAIM","The supported claim is unrelated to the target fact");
    var options=p.answerOptions()==null?List.<QuestionGenerationProviderClient.Option>of():p.answerOptions();if(options.size()<2||options.size()>6)fail("AI_QUESTION_GENERATION_OPTIONS_INVALID","Question requires two to six options");
    var keys=new HashSet<String>();var texts=new HashSet<String>();int correct=0;
    for(var option:options){required(option.optionKey(),"option key");required(option.text(),"option text");if(!keys.add(option.optionKey()))fail("AI_QUESTION_GENERATION_OPTIONS_INVALID","Duplicate option key");if(!texts.add(normalize(option.text())))fail("AI_QUESTION_GENERATION_OPTIONS_INVALID","Duplicate option text");if(INJECTION.matcher(option.text()).find()||HTML.matcher(option.text()).find())fail("AI_QUESTION_GENERATION_OPTIONS_INVALID","Unsafe option text");if(option.correct())correct++;}
    if("SINGLE_CHOICE".equals(p.questionType())&&correct!=1)fail("AI_QUESTION_GENERATION_CORRECT_ANSWER_INVALID","Single choice requires exactly one correct answer");
    if("TRUE_FALSE".equals(p.questionType())&&(options.size()!=2||correct!=1))fail("AI_QUESTION_GENERATION_CORRECT_ANSWER_INVALID","True/false requires two options and exactly one correct answer");
    if("MULTIPLE_CHOICE".equals(p.questionType())&&(correct<1||correct>=options.size()))fail("AI_QUESTION_GENERATION_CORRECT_ANSWER_INVALID","Multiple choice requires at least one but not all options to be correct");
    String supported=options.stream().filter(QuestionGenerationProviderClient.Option::correct).map(QuestionGenerationProviderClient.Option::text).reduce("",(a,b)->a+" "+b)+" "+p.explanation();
    if(!plausiblyRelated(supported,request.target().text()))fail("AI_QUESTION_GENERATION_UNSUPPORTED_CORRECT_ANSWER","The correct answer and explanation are not grounded in the target fact");
    for(var evidence:p.sourceEvidence()==null?List.<QuestionGenerationProviderClient.SourceEvidence>of():p.sourceEvidence()){
      var source=request.context().sources().stream().filter(s->s.sourceId().equals(evidence.sourceId())).findFirst().orElseThrow(()->invalid("AI_QUESTION_GENERATION_SOURCE_MISMATCH","Evidence references another Source"));
      if(!source.checksum().equals(evidence.sourceChecksum())||!source.contentExcerpt().contains(evidence.quote()))fail("AI_QUESTION_GENERATION_SOURCE_MISMATCH","Source evidence does not match the immutable snapshot");
      if(!plausiblyRelated(evidence.quote(),request.target().text()))fail("AI_QUESTION_GENERATION_UNRELATED_EVIDENCE","Source evidence is unrelated to the target fact");
    }
  }
  private boolean plausiblyRelated(String a,String b){var left=tokens(a);var right=tokens(b);left.retainAll(right);return left.size()>=1;}
  private Set<String> tokens(String value){var set=new HashSet<String>();for(String token:normalize(value).split(" "))if(token.length()>3&&!Set.of("which","what","that","this","eller","vilken","vilka","detta","samt","fråga").contains(token))set.add(token);return set;}
  static String normalize(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
  private void required(String value,String field){if(value==null||value.isBlank())fail("AI_QUESTION_GENERATION_OUTPUT_INVALID","Missing "+field);}
  private AiProviderException invalid(String code,String message){return new AiProviderException(code,false,message);}
  private void fail(String code,String message){throw invalid(code,message);}
}
