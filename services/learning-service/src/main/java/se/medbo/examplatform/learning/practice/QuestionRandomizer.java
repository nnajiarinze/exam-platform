package se.medbo.examplatform.learning.practice;

import java.util.List;

@FunctionalInterface
public interface QuestionRandomizer {
    <T> void shuffle(List<T> values);
}

