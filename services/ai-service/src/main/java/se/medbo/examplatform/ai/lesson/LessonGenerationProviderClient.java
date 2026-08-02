package se.medbo.examplatform.ai.lesson;

import java.util.List;
import java.util.UUID;

public interface LessonGenerationProviderClient {
  record Fact(UUID id,UUID versionId,String text,UUID sourceSectionId){}
  record PlannedPage(String pageType,String title,List<UUID> knowledgeFactVersionIds,
      String learnerQuestion,String pagePurpose,List<String> exactSupportingEvidence,
      List<String> allowedConcepts,List<String> forbiddenConcepts,List<String> neighbouringPageTitles){
    public PlannedPage(String pageType,String title,List<UUID> knowledgeFactVersionIds){
      this(pageType,title,knowledgeFactVersionIds,null,null,List.of(),List.of(),List.of(),List.of());
    }
  }
  record Request(UUID topicId,String topicTitle,UUID learningObjectiveId,
      String learningObjectiveTitle,UUID sourceSectionId,String sourceSectionTitle,
      String sourceSectionChecksum,String exactSourceText,List<Fact> facts,List<PlannedPage> plan,String language,
      UUID jobId,String requester,int retryAttempt){}
  record Page(String pageType,String title,String body,List<UUID> knowledgeFactVersionIds,
      List<String> evidenceQuotes,List<String> keyTerms){}
  record Proposal(String title,String introduction,String summary,List<String> importantPoints,
      List<Page> pages){}
  record Usage(Integer inputTokens,Integer outputTokens,String requestId,String provider,String model){}
  record Result(Proposal proposal,Usage usage){}
  record FailedClaim(String text,String failureCode,String diagnostic){}
  record PageRepairRequest(String topicTitle,String learningObjectiveTitle,UUID sourceSectionId,
      String sourceSectionChecksum,String exactSourceText,List<Fact> facts,Page originalPage,
      List<String> surroundingPageTitles,List<String> repairReasons,List<FailedClaim> failedClaims,
      UUID jobId,String requester,int retryAttempt){}
  record PageRepairContent(String status,String body,List<String> evidenceQuotes,List<String> keyTerms){}
  record PageRepairResult(PageRepairContent content,Usage usage){}
  Result generateLesson(Request request);
  PageRepairResult repairPage(PageRepairRequest request);
}
