package dev.codesentinel.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
    }

    record RegisterRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 100) String displayName,
                           @NotBlank @Size(min = 8, max = 200) String password, @NotBlank @Size(max = 40) String enrollmentCode) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    AuthService.AuthResponse login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r.email(), r.password());
    }

    @PostMapping("/register")
    AuthService.AuthResponse register(@Valid @RequestBody RegisterRequest r) {
        return auth.register(r.email(), r.displayName(), r.password(), r.enrollmentCode());
    }

    /** Tokens are stateless and short-lived: logout is recorded and the client discards the token (documented limitation). */
    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        CurrentUser.get();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    Principal me() {
        return CurrentUser.get();
    }
}
