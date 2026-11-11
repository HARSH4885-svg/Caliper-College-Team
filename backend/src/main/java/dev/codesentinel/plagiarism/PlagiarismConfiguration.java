package dev.codesentinel.plagiarism;

import dev.codesentinel.engine.api.PlagiarismEngine;
import dev.codesentinel.engine.similarity.SimilarityEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing the {@link PlagiarismEngine} abstraction as an injectable bean.
 * Adheres to the Dependency Inversion Principle (DIP).
 */
@Configuration
public class PlagiarismConfiguration {

    @Bean
    public PlagiarismEngine plagiarismEngine() {
        return new SimilarityEngine();
    }
}
