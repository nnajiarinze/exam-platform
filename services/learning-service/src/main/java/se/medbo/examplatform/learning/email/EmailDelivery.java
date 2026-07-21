package se.medbo.examplatform.learning.email;

import java.time.Instant;

public record EmailDelivery(String provider, String providerMessageId, Instant requestedAt) {}
