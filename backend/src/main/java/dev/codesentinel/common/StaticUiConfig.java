package dev.codesentinel.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves the built React app (frontend/dist) from the backend itself so production has one origin and one port. */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnExpression("!'${app.static-dir:}'.isEmpty()")
public class StaticUiConfig implements WebMvcConfigurer {
    private final Path root;

    public StaticUiConfig(AppProperties props) {
        this.root = Path.of(props.staticDir()).toAbsolutePath().normalize();
    }

    @Override
    public void addViewControllers(org.springframework.web.servlet.config.annotation.ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations(root.toUri().toString()).resourceChain(true).addResolver(new PathResourceResolver() {
            @Override
            protected Resource getResource(String resourcePath, Resource location) throws IOException {
                if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator")) return null;
                Path p = root.resolve(resourcePath).normalize();
                if (p.startsWith(root) && Files.isRegularFile(p)) return new FileSystemResource(p);
                // client-side route (e.g. /courses/3): hand the SPA its entry point
                return resourcePath.contains(".") && !resourcePath.equals(".") ? null : new FileSystemResource(root.resolve("index.html"));
            }
        });
    }
}
