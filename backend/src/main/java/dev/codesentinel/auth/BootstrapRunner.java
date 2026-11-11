package dev.codesentinel.auth;

import dev.codesentinel.common.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first instructor from environment values. There is no public instructor sign-up. */
@Component
public class BootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);
    private final AppProperties props;
    private final UserRepository users;

    public BootstrapRunner(AppProperties props, UserRepository users) {
        this.props = props;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = props.bootstrap().instructorEmail(), pw = props.bootstrap().instructorPassword();
        if (email.isBlank() || pw.isBlank()) return;
        if (pw.length() < 8) {
            log.warn("BOOTSTRAP_INSTRUCTOR_PASSWORD is shorter than 8 characters; instructor not created");
            return;
        }
        if (users.findByEmail(email).isPresent()) return;
        users.insert(email, "Instructor", new BCryptPasswordEncoder(10).encode(pw), "INSTRUCTOR", false);
        log.info("Bootstrap instructor created");
    }
}
