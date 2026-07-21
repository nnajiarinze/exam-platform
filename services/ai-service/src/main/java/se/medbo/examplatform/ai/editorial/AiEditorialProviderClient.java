package se.medbo.examplatform.ai.editorial;
import java.util.List;import java.util.Map;import java.util.UUID;
public interface AiEditorialProviderClient {
 Result execute(Request request);
 record Target(UUID factId,UUID factVersionId,long version,String text,String checksum){}
 record Source(UUID sourceId,String title,String text,String checksum){}
 record Request(EditorialOperationType operation,List<Target> targets,List<Source> sources,UUID learningObjectiveId,String objectiveTitle,String language,String readingPreference,String instruction,int count,String promptVersion,UUID jobId,String requester,int retryAttempt){
   public Request(EditorialOperationType operation,List<Target> targets,List<Source> sources,UUID learningObjectiveId,String objectiveTitle,String language,String readingPreference,String instruction,int count,String promptVersion){this(operation,targets,sources,learningObjectiveId,objectiveTitle,language,readingPreference,instruction,count,promptVersion,null,null,0);}
   Request execution(UUID id,String actor,int retry){return new Request(operation,targets,sources,learningObjectiveId,objectiveTitle,language,readingPreference,instruction,count,promptVersion,id,actor,retry);}
 }
 record Evidence(UUID sourceId,String sourceTitle,String quote,String location){}
 record Revision(UUID targetFactId,String proposedText,String rationale,List<Evidence> evidence,List<String>warnings,Map<String,Object>coverage,String confidence){}
 record Finding(String type,String severity,UUID targetFactId,String title,String message,String affectedPhrase,List<Evidence>evidence,String confidence,String suggestedAction,Map<String,Object>details){}
 record Usage(Integer inputTokens,Integer outputTokens,String requestId){}
 record Result(List<Revision> revisions,List<Finding> findings,List<String>warnings,Usage usage){}
}
