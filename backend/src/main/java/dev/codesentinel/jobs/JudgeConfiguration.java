package dev.codesentinel.jobs;

import dev.codesentinel.common.AppProperties;
import dev.codesentinel.judge.Judge;
import dev.codesentinel.judge.JudgeConfig;
import dev.codesentinel.judge.sandbox.DockerRunner;
import dev.codesentinel.judge.sandbox.DockerSandboxProvider;
import dev.codesentinel.judge.sandbox.GVisorSandboxProvider;
import dev.codesentinel.judge.sandbox.SandboxExecutionProvider;
import dev.codesentinel.judge.sandbox.SandboxProviderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

@Configuration
@EnableScheduling
public class JudgeConfiguration {

    @Bean
    public SandboxProviderRegistry sandboxProviderRegistry(AppProperties props) {
        return new SandboxProviderRegistry(props.judge().dockerBin(), props.judge().sandboxRuntime());
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public SandboxExecutionProvider sandboxExecutionProvider(SandboxProviderRegistry registry) {
        return registry.getActiveProvider();
    }

    @Bean
    public DockerRunner dockerRunner(AppProperties props) {
        return new DockerRunner(props.judge().dockerBin());
    }

    @Bean
    public Judge judge(SandboxExecutionProvider runner, AppProperties props) {
        JudgeConfig d = JudgeConfig.defaults();
        Path work = props.judge().workRoot().isBlank() ? d.workRoot() : Path.of(props.judge().workRoot());
        return new Judge(runner, new JudgeConfig(work, d.compileTimeoutMs(), d.compileMemoryMb(), d.jobTimeoutMs(), d.startupAllowanceMs(), d.pidsLimit(), d.cpus(),
                d.tmpfsMb(), d.minTimeMs(), d.maxTimeMs(), d.minMemoryMb(), d.maxMemoryMb(), d.excerptBytes()));
    }
}
