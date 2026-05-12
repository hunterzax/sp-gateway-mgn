package com.ptt.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class AzureOAuthService {

    // Credentials MUST be injected via environment variables, never from property files
    // (CWE-260 remediation — no default values to prevent accidental config-file exposure)
    @Value("${azure.tenant-id:}")
    private String tenantId;

    @Value("${azure.client-id:}")
    private String clientId;

    @Value("${AZURE_CLIENT_SECRET:}")
    private String clientSecret;

    @jakarta.annotation.PostConstruct
    public void validateConfig() {
        if (clientSecret == null || clientSecret.isEmpty() || clientSecret.contains("${")) {
            log.warn("\n###################################################################\n" +
                    "## WARNING: AZURE_CLIENT_SECRET is not set!                      ##\n" +
                    "## Azure OAuth code-exchange will fail without a client secret.   ##\n" +
                    "## Set AZURE_CLIENT_SECRET in your environment variables (.env)   ##\n" +
                    "###################################################################");
        }
    }

    private final RestTemplate rest = new RestTemplate();

    public Map<String, Object> exchangeCodeForToken(String code, String redirectUri) {
        String tokenUrl = String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/token", tenantId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isEmpty()) {
            body.add("client_secret", clientSecret);
        }
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = rest.postForEntity(tokenUrl, req, Map.class);
        return resp.getBody();
    }
}
