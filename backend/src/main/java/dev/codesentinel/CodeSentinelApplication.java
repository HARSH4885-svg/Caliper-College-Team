package dev.codesentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.boot.context.properties.ConfigurationPropertiesScan
@SpringBootApplication
public class CodeSentinelApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeSentinelApplication.class, args);
    }
}
