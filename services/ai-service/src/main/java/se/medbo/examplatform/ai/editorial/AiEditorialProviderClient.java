package se.medbo.examplatform.ai.editorial;
import java.util.List;import java.util.Map;import java.util.UUID;
public interface AiEditorialProviderClient {
 Result execute(Request request);
 record Target(UUID factId,UUID factVersionId,long version,String text,String checksum){}
 record Source(UUID sourceId,String text,String checksum){}
 record Request(EditorialOperationType operation,List<Target> targets,List<Source> sources,UUID learningObjectiveId,String objectiveTitle,String language,String readingPreference,String instruction,int count,String promptVersion){}
 record Evidence(UUID sourceId,String quote,String location){}
 record Revision(UUID targetFactId,String proposedText,String rationale,List<Evidence> evidence,List<String>warnings,Map<String,Object>coverage,String confidence){}
 record Finding(String type,String severity,UUID targetFactId,String title,String message,String affectedPhrase,List<Evidence>evidence,String confidence,String suggestedAction,Map<String,Object>details){}
 record Usage(Integer inputTokens,Integer outputTokens,String requestId){}
 record Result(List<Revision> revisions,List<Finding> findings,List<String>warnings,Usage usage){}
}
