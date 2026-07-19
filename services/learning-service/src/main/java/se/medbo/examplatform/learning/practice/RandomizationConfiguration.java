package se.medbo.examplatform.learning.practice;

import java.security.SecureRandom;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RandomizationConfiguration {
    @Bean
    QuestionRandomizer questionRandomizer() {
        var random = new SecureRandom();
        return new QuestionRandomizer() {
            @Override
            public <T> void shuffle(java.util.List<T> values) {
                Collections.shuffle(values, random);
            }
        };
    }
}

