package se.medbo.examplatform.learning.learner;

import java.time.Instant;
import java.util.UUID;

public record LearnerProfile(UUID id, String externalIdentityId, String displayName,
        String interfaceLanguage, String explanationLanguage, Instant createdAt, Instant updatedAt) {}

