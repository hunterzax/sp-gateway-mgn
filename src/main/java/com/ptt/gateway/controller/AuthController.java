package com.ptt.gateway.controller;

import com.ptt.gateway.dto.AuthRequest;
import com.ptt.gateway.dto.AuthResponse;
import com.ptt.gateway.dto.LoginResponse;
import com.ptt.gateway.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ptt.gateway.dto.CodeLoginRequest;
import com.ptt.gateway.service.AzureOAuthService;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AzureOAuthService azureOAuthService;

    public AuthController(AuthService authService, AzureOAuthService azureOAuthService) {
        this.authService = authService;
        this.azureOAuthService = azureOAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody AuthRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request, httpRequest);

        // Create HttpOnly Cookie
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt", authResponse.getToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production (HTTPS)
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60); // 1 hour

        response.addCookie(cookie);

        return ResponseEntity.ok(new LoginResponse("success", "Login successful"));
    }

    @PostMapping("/ad")
    public ResponseEntity<LoginResponse> adLogin(@RequestBody com.ptt.gateway.dto.AdLoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse response) {
        AuthResponse authResponse = authService.loginAd(request.getAccess_token(), httpRequest);

        // Create HttpOnly Cookie
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt", authResponse.getToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production (HTTPS)
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60); // 1 hour

        response.addCookie(cookie);

        return ResponseEntity.ok(new LoginResponse("success", "AD Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(jakarta.servlet.http.HttpServletResponse response) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0); // Expire immediately

        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ad/code")
    public ResponseEntity<LoginResponse> adLoginWithCode(@RequestBody CodeLoginRequest request,
                                                         jakarta.servlet.http.HttpServletRequest httpRequest,
                                                         jakarta.servlet.http.HttpServletResponse response) {
        String code = request.getCode();
        String redirect = request.getRedirect_uri();

        try {
            Map<String, Object> tokenResp = azureOAuthService.exchangeCodeForToken(code, redirect);
            if (tokenResp == null || tokenResp.get("access_token") == null) {
                return ResponseEntity.status(500).body(new LoginResponse("error", "Token exchange failed"));
            }

            String accessToken = tokenResp.get("access_token").toString();
            AuthResponse authResponse = authService.loginAd(accessToken, httpRequest);

            // Create HttpOnly Cookie
            jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt", authResponse.getToken());
            cookie.setHttpOnly(true);
            cookie.setSecure(false); // Set to true in production (HTTPS)
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60); // 1 hour

            response.addCookie(cookie);

            return ResponseEntity.ok(new LoginResponse("success", "AD Login successful (code-exchange)"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new LoginResponse("error", "AD code exchange failed"));
        }
    }
}
