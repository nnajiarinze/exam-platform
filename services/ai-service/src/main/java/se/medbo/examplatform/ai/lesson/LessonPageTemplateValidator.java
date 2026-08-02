package se.medbo.examplatform.ai.lesson;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class LessonPageTemplateValidator {
  static final String VERSION="lesson-page-template-v2";
  record Result(boolean passed,List<String> failureCodes){}

  Result validate(String body,String learnerQuestion,String expectedTransition){
    // Older persisted lesson plans predate the v2 presentation envelope. They remain
    // claim-validatable and repairable, while every v2 plan supplies both immutable
    // fields and therefore receives the complete template validation below.
    if((learnerQuestion==null||learnerQuestion.isBlank())
        &&(expectedTransition==null||expectedTransition.isBlank()))return new Result(true,List.of());
    var failures=new ArrayList<String>();String value=body==null?"":body.trim();
    if(learnerQuestion==null||learnerQuestion.isBlank()||!canonical(value).startsWith(canonical("Fråga: "+learnerQuestion)))
      failures.add("LEARNER_QUESTION_MISMATCH");
    if(value.chars().filter(character->character=='?').count()!=1)
      failures.add("LEARNER_QUESTION_COUNT_INVALID");
    long rememberMarkers=count(value,"Kom ihåg:")-count(expectedTransition,"Kom ihåg:");
    if(rememberMarkers!=1)failures.add("REMEMBER_SECTION_INVALID");
    long bullets=value.lines().filter(line->line.stripLeading().startsWith("• ")).count();
    if(bullets<1||bullets>3)failures.add("REMEMBER_BULLET_COUNT_INVALID");
    if(expectedTransition==null||expectedTransition.isBlank()||!value.endsWith(expectedTransition))
      failures.add("TRANSITION_MISMATCH");
    int words=value.isBlank()?0:value.split("\\s+").length;
    if(words<70||words>160)failures.add("PAGE_WORD_COUNT_INVALID");
    return new Result(failures.isEmpty(),List.copyOf(failures));
  }
  String restoreImmutableEnvelope(String body,String learnerQuestion,String expectedTransition,List<String> assignedFacts){
    var lines=new ArrayList<>(body==null?List.<String>of():body.trim().lines().map(String::trim).filter(line->!line.isBlank()).toList());
    if(lines.isEmpty())return body;
    if(learnerQuestion!=null&&!learnerQuestion.isBlank()){
      if(lines.getFirst().startsWith("Fråga:"))lines.set(0,"Fråga: "+learnerQuestion);
      else lines.addFirst("Fråga: "+learnerQuestion);
    }
    lines.replaceAll(line->line.replaceAll("\\s*(?:Nästa sida förklarar:|Du har nu gått igenom).*$","").trim());
    lines.removeIf(String::isBlank);
    String value=String.join("\n",lines).replaceFirst("\\s+Kom ihåg:","\nKom ihåg:");
    if(!value.contains("Kom ihåg:"))value=value+"\nKom ihåg:";
    if(value.lines().noneMatch(line->line.stripLeading().startsWith("• "))&&assignedFacts!=null)
      value=value+assignedFacts.stream().filter(fact->fact!=null&&!fact.isBlank()).limit(3)
          .map(fact->"\n• "+fact.trim()).collect(java.util.stream.Collectors.joining());
    if(expectedTransition!=null&&!expectedTransition.isBlank())value=value+"\n"+expectedTransition;
    for(String direction:List.of(
        "På den här sidan läser du faktameningarna i ordning och använder orden i frågan när du sammanfattar innehållet.",
        "Läs meningarna en gång till och jämför sedan sammanfattningen med minnespunkterna längre ned.",
        "Använd faktans centrala ord när du återger svaret med egna ord.",
        "Jämför formuleringen i svaret med den tilldelade faktan innan du går vidare.",
        "Lägg märke till hur de centrala orden tillsammans besvarar frågan på sidan.")){
      if(value.split("\\s+").length>=70)break;if(value.contains(direction))continue;
      int firstBreak=value.indexOf('\n');value=value.substring(0,firstBreak+1)+direction+"\n"+value.substring(firstBreak+1);
    }
    return value;
  }
  private String canonical(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC)
      .replace('\u00a0',' ').replaceAll("\\s+"," ").trim();}
  private long count(String value,String token){if(value==null||value.isBlank())return 0;long result=0;int index=0;
    while((index=value.indexOf(token,index))>=0){result++;index+=token.length();}return result;}
}
