package se.medbo.examplatform.ai.lesson;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
final class LessonPageClaimValidator {
  static final String VERSION="lesson-page-claim-v1";
  record Claim(int order,String text,String status,String failureCode,String diagnostic,List<String> evidence){}
  record Result(boolean supported,List<Claim> claims,List<String> failureCodes){}
  private static final Pattern CAUSAL=Pattern.compile("(?iu)\\b(orsakar|leder till|resulterar i|gör att|medför|därför|vilket kan|kan leda|får .* att)\\b");
  private static final Pattern GENERAL=Pattern.compile("(?iu)\\b(alltid|aldrig|alla människor|grundpelare|nödvändig(?:a|t)?|garanterar|oproportionerligt|manipulera|polariserar?)\\b");
  private static final Pattern TRANSITION=Pattern.compile("(?iu)^(?:fråga:|i den här lektionen\\b|på den här sidan\\b|läs\\b|jämför\\b|använd\\b|lägg märke till\\b|kom ihåg:?|sammanfattningsvis\\b|läs vidare\\b|nästa sida förklarar:|du har nu gått igenom\\b)");
  private static final Set<String> STOP=Set.of("och","att","det","den","de","ett","en","som","för","på","i","av","till","med","kan","är","har","också","sig","sin","sina","sitt","där","när","så","om");

  Result validate(LessonGenerationProviderClient.Page page,String source,List<String> facts){
    String normalizedSource=normalize(source);var normalizedFacts=facts.stream().map(this::normalize).toList();
    var claims=new ArrayList<Claim>();var failures=new LinkedHashSet<String>();var seen=new LinkedHashSet<String>();int order=0;
    for(String raw:page.body().split("(?<=[.!?])\\s+|\\R+")){
      String text=raw.trim();if(text.isBlank())continue;
      if(TRANSITION.matcher(text).find()){claims.add(new Claim(order++,text,"NON_FACTUAL_TEXT","NON_FACTUAL_TEXT","Instructional or transitional text",List.of()));continue;}
      String normalized=normalize(text);String code=null;String diagnostic=null;
      boolean exact=normalizedSource.contains(normalized)||normalizedFacts.stream().anyMatch(f->f.contains(normalized)||normalized.contains(f));
      String withoutNegation=normalized.replaceAll("\\b(inte|aldrig|ingen|inget|inga)\\b","").replaceAll("\\s+"," ").trim();
      boolean rememberBullet=text.stripLeading().startsWith("• ");
      if(!rememberBullet&&!seen.add(normalized)){code="DUPLICATE_CLAIM";diagnostic="The same substantive claim already occurs on this page.";}
      else if(rememberBullet)seen.add(normalized);
      else if(!exact&&containsNegation(normalized)&&normalizedSource.contains(withoutNegation)){code="CONTRADICTION";diagnostic="The claim negates a statement present in the bounded source.";}
      else if(CAUSAL.matcher(text).find()&&!exact){code="UNSUPPORTED_CAUSALITY";diagnostic="Causal or consequential relationship is not stated directly in the bounded source.";}
      else if(GENERAL.matcher(text).find()&&!exact){code="UNSUPPORTED_GENERALIZATION";diagnostic="The claim is broader than the bounded source or assigned Facts.";}
      else if(!exact&&supportRatio(normalized,normalizedSource,normalizedFacts)<0.72){code="UNSUPPORTED_CLAIM";diagnostic="The substantive claim lacks sufficient direct lexical support in the bounded source or assigned Facts.";}
      List<String> evidence=page.evidenceQuotes()==null?List.of():page.evidenceQuotes().stream().filter(q->normalizedSource.contains(normalize(q))).limit(3).toList();
      if(code==null&&evidence.isEmpty()){code="MISSING_EVIDENCE";diagnostic="No exact or safely normalized page evidence occurs in the bounded source.";}
      if(code==null)claims.add(new Claim(order++,text,"SUPPORTED",null,"Supported by bounded source or assigned Fact.",evidence));
      else{failures.add(code);claims.add(new Claim(order++,text,"REJECTED",code,diagnostic,evidence));}
    }
    if(claims.isEmpty()){failures.add("MISSING_EVIDENCE");claims.add(new Claim(0,page.body(),"REJECTED","MISSING_EVIDENCE","Page contains no validateable content.",List.of()));}
    return new Result(failures.isEmpty(),List.copyOf(claims),List.copyOf(failures));
  }

  private double supportRatio(String claim,String source,List<String> facts){
    var words=significant(claim);if(words.isEmpty())return 0;var available=significant(source);facts.forEach(f->available.addAll(significant(f)));
    long present=words.stream().filter(available::contains).count();return present*1.0/words.size();
  }
  private Set<String> significant(String value){var result=new LinkedHashSet<String>();Arrays.stream(value.split(" ")).filter(w->w.length()>=4&&!STOP.contains(w)).forEach(result::add);return result;}
  private boolean containsNegation(String value){return Pattern.compile("\\b(inte|aldrig|ingen|inget|inga)\\b").matcher(value).find();}
  private String normalize(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
      .replace('\u00a0',' ').replaceAll("-\\s+","").replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim();}
}
