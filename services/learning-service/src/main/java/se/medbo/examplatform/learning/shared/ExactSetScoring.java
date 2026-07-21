package se.medbo.examplatform.learning.shared;

import java.util.Collection;
import java.util.HashSet;

public final class ExactSetScoring {
    private ExactSetScoring() {}

    public static boolean matches(Collection<String> selectedOptionIds, Collection<String> correctOptionIds) {
        return selectedOptionIds.size() == new HashSet<>(selectedOptionIds).size()
                && new HashSet<>(selectedOptionIds).equals(new HashSet<>(correctOptionIds));
    }
}
