package se.medbo.examplatform.ai.generation;

import java.util.HashSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.medbo.examplatform.ai.provider.AiProviderClient.GenerationResult;
import se.medbo.examplatform.ai.provider.AiProviderException;

@Component
public final class StructuredOutputValidator {
    private final int maxLength;
    public StructuredOutputValidator(@Value("${ai.editorial.max-fact-characters:500}")int maxLength){this.maxLength=maxLength;}
    public void validate(GenerationResult result,String source,int requested){
        if(result==null||result.proposals()==null||result.proposals().isEmpty())fail("No supported proposals were generated");
        if(result.proposals().size()>requested)fail("The provider returned too many proposals");
        var normalized=new HashSet<String>();
        result.proposals().forEach(p->{
            if(p.text()==null||p.text().isBlank())fail("A proposal was empty");
            if(p.text().length()>maxLength)fail("A proposal exceeded the maximum fact length");
            if(p.text().contains("<")||p.text().contains(">"))fail("HTML is not allowed in proposals");
            String key=normalize(p.text());if(!normalized.add(key))fail("The provider returned duplicate proposals");
            if(p.sourceEvidence()==null||p.sourceEvidence().isEmpty())fail("Every proposal requires source evidence");
            p.sourceEvidence().forEach(e->{if(e.quote()==null||e.quote().isBlank()||!source.contains(e.quote()))fail("Proposal evidence was not found in the supplied source");});
        });
    }
    public static String normalize(String s){return s.trim().replaceAll("\\s+"," ").toLowerCase();}
    private void fail(String message){throw new AiProviderException("AI_STRUCTURED_OUTPUT_INVALID",false,message);}
}
