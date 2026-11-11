package dev.codesentinel.storage;

import dev.codesentinel.common.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class StorageConfig {
    @Bean
    FileStorageProvider fileStorageProvider(AppProperties props) {
        return new LocalFileStorageProvider(Path.of(props.storage().root()));
    }
}
