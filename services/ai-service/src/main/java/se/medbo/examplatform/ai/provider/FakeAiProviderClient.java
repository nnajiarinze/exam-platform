package se.medbo.examplatform.ai.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="ai.editorial.provider",havingValue="FAKE",matchIfMissing=true)
final class FakeAiProviderClient implements AiProviderClient {
    public GenerationResult generate(GenerationRequest request) {
        if(request.sourceText().contains("[[SIMULATE_TIMEOUT]]")) throw new AiProviderException("AI_REQUEST_TIMEOUT",true,"The AI provider timed out");
        if(request.sourceText().contains("[[SIMULATE_RATE_LIMIT]]")) throw new AiProviderException("AI_PROVIDER_RATE_LIMITED",true,"The AI provider rate limit was reached");
        if(request.sourceText().contains("[[SIMULATE_MALFORMED]]")) throw new AiProviderException("AI_STRUCTURED_OUTPUT_INVALID",false,"The provider returned invalid structured output");
        var sentences=Arrays.stream(request.sourceText().split("(?<=[.!?])\\s+|\\R+"))
            .map(String::trim).filter(s->!s.isBlank()).filter(s->!looksLikeInstruction(s)).distinct().toList();
        var proposals=new ArrayList<Proposal>();
        for(int i=0;i<Math.min(request.count(),sentences.size());i++) {
            String sentence=sentences.get(i);
            proposals.add(new Proposal(sentence,List.of(new Evidence(sentence,"Source text, statement "+(i+1))),"HIGH","Directly supported by the supplied source."));
        }
        var warnings=proposals.size()<request.count()?List.of("The source did not contain enough distinct supported statements."):List.<String>of();
        return new GenerationResult(proposals,warnings,new Usage(Math.max(1,request.sourceText().length()/4),Math.max(1,proposals.stream().mapToInt(p->p.text().length()).sum()/4),"fake-"+UUID.randomUUID()));
    }
    private boolean looksLikeInstruction(String value){String v=value.toLowerCase();return v.contains("ignore previous instructions")||v.contains("ignore all instructions")||v.contains("system prompt")||v.contains("assistant:");}
}
