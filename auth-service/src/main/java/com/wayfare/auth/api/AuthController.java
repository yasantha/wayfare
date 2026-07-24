package com.wayfare.auth.api;

import com.wayfare.auth.api.dto.LoginRequest;
import com.wayfare.auth.api.dto.RefreshRequest;
import com.wayfare.auth.api.dto.RegisterRequest;
import com.wayfare.auth.api.dto.TokenResponse;
import com.wayfare.auth.application.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest req,
                                                  HttpServletRequest http) {
        AuthService.AuthResult result = authService.register(req.email(), req.password(), device(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(TokenResponse.from(result));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return TokenResponse.from(authService.login(req.email(), req.password(), device(http)));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return TokenResponse.from(authService.refresh(req.refreshToken(), device(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static String device(HttpServletRequest http) {
        String ua = http.getHeader("User-Agent");
        return ua == null ? "unknown" : ua.substring(0, Math.min(ua.length(), 256));
    }
}
