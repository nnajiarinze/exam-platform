package se.medbo.examplatform.ai.provider;

import java.util.List;

public interface AiProviderClient {
    GenerationResult generate(GenerationRequest request);
    record GenerationRequest(String sourceText,String objective,String language,int count,String instruction,String promptVersion,java.util.UUID jobId,String requester,int retryAttempt) {
        public GenerationRequest(String sourceText,String objective,String language,int count,String instruction,String promptVersion){this(sourceText,objective,language,count,instruction,promptVersion,null,null,0);}
    }
    record Evidence(String quote,String location) {}
    record Proposal(String text,List<Evidence> sourceEvidence,String confidence,String notes) {}
    record Usage(Integer inputTokens,Integer outputTokens,String requestId) {}
    record GenerationResult(List<Proposal> proposals,List<String> warnings,Usage usage) {}
}
