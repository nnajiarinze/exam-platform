package se.medbo.examplatform.content.release;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

final class ReleaseOptionOrderingPolicy {
    static final String VERSION = "release-option-order-v1";

    Map<UUID, Integer> assignCorrectPositions(String corpusId, String releaseVersion,
                                               List<QuestionShape> questions) {
        String releaseSeed = seed(corpusId, releaseVersion);
        var ordered = new ArrayList<>(questions);
        ordered.sort(Comparator.comparing(question -> digestHex(releaseSeed + "|question|" + question.id())));

        var usage = new HashMap<Integer, Integer>();
        var result = new HashMap<UUID, Integer>();
        Integer previous = null;
        for (var question : ordered) {
            if (question.optionCount() < 2) {
                throw new IllegalArgumentException("A released question must have at least two options");
            }
            int minimum = java.util.stream.IntStream.range(0, question.optionCount())
                    .map(position -> usage.getOrDefault(position, 0)).min().orElseThrow();
            var candidates = java.util.stream.IntStream.range(0, question.optionCount())
                    .filter(position -> usage.getOrDefault(position, 0) == minimum).boxed().toList();
            if (previous != null && candidates.size() > 1) {
                int prior = previous;
                candidates = candidates.stream().filter(position -> position != prior).toList();
            }
            int selection = Math.floorMod(longHash(releaseSeed + "|target|" + question.id()), candidates.size());
            int target = candidates.get(selection);
            result.put(question.id(), target);
            usage.merge(target, 1, Integer::sum);
            previous = target;
        }
        return Map.copyOf(result);
    }

    <T> List<T> order(String corpusId, String releaseVersion, UUID questionId, List<T> options,
                      java.util.function.Predicate<T> correct, int targetPosition) {
        var correctOptions = options.stream().filter(correct).toList();
        if (correctOptions.size() != 1 || targetPosition < 0 || targetPosition >= options.size()) {
            throw new IllegalArgumentException("Single-choice release ordering requires one correct option and a valid target");
        }
        var distractors = new ArrayList<>(options.stream().filter(correct.negate()).toList());
        Collections.shuffle(distractors, new Random(longHash(seed(corpusId, releaseVersion)
                + "|distractors|" + questionId)));
        var ordered = new ArrayList<T>(options.size());
        int distractorIndex = 0;
        for (int position = 0; position < options.size(); position++) {
            ordered.add(position == targetPosition ? correctOptions.getFirst() : distractors.get(distractorIndex++));
        }
        return List.copyOf(ordered);
    }

    <T> List<T> shuffle(String corpusId, String releaseVersion, UUID questionId, List<T> options) {
        var ordered = new ArrayList<>(options);
        Collections.shuffle(ordered, new Random(longHash(seed(corpusId, releaseVersion)
                + "|all-options|" + questionId)));
        return List.copyOf(ordered);
    }

    private String seed(String corpusId, String releaseVersion) {
        return corpusId + "|" + releaseVersion + "|" + VERSION;
    }

    private long longHash(String value) {
        return ByteBuffer.wrap(digest(value)).getLong();
    }

    private String digestHex(String value) {
        return java.util.HexFormat.of().formatHex(digest(value));
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record QuestionShape(UUID id, int optionCount) {}
}
