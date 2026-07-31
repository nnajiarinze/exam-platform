package se.medbo.examplatform.ai.lesson;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="ai.editorial.provider",havingValue="FAKE",matchIfMissing=true)
final class FakeLessonGenerationProviderClient implements LessonGenerationProviderClient {
  @Override public Result generateLesson(Request request){
    var statements=request.facts().stream().map(Fact::text).toList();
    return new Result(new Proposal(request.topicTitle(),statements,List.of(request.topicTitle())),
        new Usage(100,50,"fake-lesson-"+request.jobId()));
  }
}
