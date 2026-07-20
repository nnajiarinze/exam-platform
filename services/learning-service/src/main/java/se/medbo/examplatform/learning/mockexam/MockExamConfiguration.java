package se.medbo.examplatform.learning.mockexam;

import java.security.SecureRandom;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class MockExamConfiguration {
    @Bean
    MockExamGenerator.Randomizer mockExamRandomizer() {
        var random = new SecureRandom();
        return new MockExamGenerator.Randomizer() {
            @Override
            public <T> void shuffle(java.util.List<T> values) {
                Collections.shuffle(values, random);
            }
        };
    }
}
