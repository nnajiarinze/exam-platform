package se.medbo.examplatform.ai.question;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class QuestionIntelligencePolicy {
  final String engineVersion;final int wordsPerMinute,minimumReadingSeconds,shortQuestionWords,longQuestionWords,maximumQuestionWords,maximumOptionWords,minimumExplanationWords,maximumExplanationWords;
  final int excellentThreshold,goodThreshold,acceptableThreshold,needsReviewThreshold;
  final int groundingWeight,structureWeight,readabilityWeight,completenessWeight,metadataWeight;
  QuestionIntelligencePolicy(
      @Value("${ai.question-intelligence.engine-version:question-intelligence-v1}")String engineVersion,
      @Value("${ai.question-intelligence.words-per-minute:180}")int wordsPerMinute,
      @Value("${ai.question-intelligence.minimum-reading-seconds:5}")int minimumReadingSeconds,
      @Value("${ai.question-intelligence.short-question-words:4}")int shortQuestionWords,
      @Value("${ai.question-intelligence.long-question-words:35}")int longQuestionWords,
      @Value("${ai.question-intelligence.maximum-question-words:80}")int maximumQuestionWords,
      @Value("${ai.question-intelligence.maximum-option-words:30}")int maximumOptionWords,
      @Value("${ai.question-intelligence.minimum-explanation-words:4}")int minimumExplanationWords,
      @Value("${ai.question-intelligence.maximum-explanation-words:120}")int maximumExplanationWords,
      @Value("${ai.question-intelligence.thresholds.excellent:90}")int excellentThreshold,
      @Value("${ai.question-intelligence.thresholds.good:80}")int goodThreshold,
      @Value("${ai.question-intelligence.thresholds.acceptable:65}")int acceptableThreshold,
      @Value("${ai.question-intelligence.thresholds.needs-review:45}")int needsReviewThreshold,
      @Value("${ai.question-intelligence.weights.grounding:30}")int groundingWeight,
      @Value("${ai.question-intelligence.weights.structure:25}")int structureWeight,
      @Value("${ai.question-intelligence.weights.readability:20}")int readabilityWeight,
      @Value("${ai.question-intelligence.weights.completeness:15}")int completenessWeight,
      @Value("${ai.question-intelligence.weights.metadata-consistency:10}")int metadataWeight){this.engineVersion=engineVersion;this.wordsPerMinute=wordsPerMinute;this.minimumReadingSeconds=minimumReadingSeconds;this.shortQuestionWords=shortQuestionWords;this.longQuestionWords=longQuestionWords;this.maximumQuestionWords=maximumQuestionWords;this.maximumOptionWords=maximumOptionWords;this.minimumExplanationWords=minimumExplanationWords;this.maximumExplanationWords=maximumExplanationWords;this.excellentThreshold=excellentThreshold;this.goodThreshold=goodThreshold;this.acceptableThreshold=acceptableThreshold;this.needsReviewThreshold=needsReviewThreshold;this.groundingWeight=groundingWeight;this.structureWeight=structureWeight;this.readabilityWeight=readabilityWeight;this.completenessWeight=completenessWeight;this.metadataWeight=metadataWeight;}
  @PostConstruct void validate(){if(wordsPerMinute<=0||minimumReadingSeconds<1||maximumQuestionWords<=longQuestionWords||excellentThreshold<=goodThreshold||goodThreshold<=acceptableThreshold||acceptableThreshold<=needsReviewThreshold||groundingWeight+structureWeight+readabilityWeight+completenessWeight+metadataWeight!=100)throw new IllegalStateException("AI_QUESTION_INTELLIGENCE_CONFIG_INVALID");}
}
