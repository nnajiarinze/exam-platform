package se.medbo.examplatform.ai.lesson;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="ai.editorial.provider",havingValue="FAKE",matchIfMissing=true)
final class FakeLessonGenerationProviderClient implements LessonGenerationProviderClient {
  @Override public Result generateLesson(Request request){
    var pages=request.plan().stream().map(page->new Page(page.pageType(),page.title(),
        page.title()+". "+request.facts().stream().filter(f->page.knowledgeFactVersionIds().contains(f.versionId()))
            .map(Fact::text).collect(java.util.stream.Collectors.joining(" "))
            +" Detta avsnitt hjälper läraren att läsa den godkända uppgiften i sitt ämnessammanhang. "
            +"Texten håller sig till underlaget och markerar vad som är viktigast att minnas. "
            +"Läs innehållet lugnt och jämför rubriken med den centrala uppgiften innan du går vidare.",
        page.knowledgeFactVersionIds(),List.of(request.facts().getFirst().text()),List.of(request.topicTitle()))).toList();
    return new Result(new Proposal(request.topicTitle(),"Introduktion", "Sammanfattning",
        request.facts().stream().map(Fact::text).toList(),pages),
        new Usage(100,50,"fake-lesson-"+request.jobId(),"FAKE","deterministic-v1"));
  }
}
