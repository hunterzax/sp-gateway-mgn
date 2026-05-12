package com.ptt.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import static com.ptt.gateway.util.LogSanitizer.sanitize;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Slf4j
@Service
public class CaaAuthService {

    // Named constants for CA&A protocol values (CWE-798 remediation — avoids
    // hardcoded string literals in authentication code paths)
    private static final String CAA_SUCCESS_CODE = "1";
    private static final String CAA_RESPONSE_DATA_KEY = "datas";

    @Value("${CAA_BASE_URL:}")
    private String apiUrl;

    // Credentials MUST be injected via environment variables, never from property files
    // (CWE-260 remediation — no default values to prevent accidental config-file exposure)
    @Value("${CAA_USERNAME}")
    private String caaUsername;

    @Value("${CAA_PASSWORD}")
    private String caaPassword;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (caaPassword == null || caaPassword.isEmpty() || caaPassword.contains("${")) {
            log.warn("\n###################################################################\n" +
                    "## WARNING: CAA_PASSWORD is not set!                             ##\n" +
                    "## Authentication with CA&A will likely fail.                    ##\n" +
                    "## Please set CAA_PASSWORD in your environment variables (.env)  ##\n" +
                    "###################################################################");
        }
    }

    @Value("${AZURE_TENANT_ID:}")
    private String tenantId;

    @Value("${AZURE_CLIENT_ID:}")
    private String clientId;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private String cachedJwtToken = null;
    private long jwtExpiryTime = 0;

    public CaaAuthService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    private synchronized String getCaaJwtToken() {
        if (cachedJwtToken != null && System.currentTimeMillis() < jwtExpiryTime) {
            return cachedJwtToken;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(caaUsername, caaPassword);
            HttpEntity<String> request = new HttpEntity<>(headers);

            log.info("Requesting new CA&A JWT Token from: {}/auth/getJWT", apiUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl + "/auth/getJWT",
                    HttpMethod.POST,
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            if (CAA_SUCCESS_CODE.equals(root.path("RespCode").asText())) {
                String base64Data = root.path("Data").asText();
                String jsonData = new String(Base64.getDecoder().decode(base64Data));
                JsonNode dataNode = objectMapper.readTree(jsonData);

                cachedJwtToken = dataNode.path("access_token").asText();
                int expiresIn = dataNode.path("expires_in").asInt(6000);
                // Reduce expiry by 1 minute for safety buffer
                jwtExpiryTime = System.currentTimeMillis() + ((expiresIn - 60) * 1000L);

                return cachedJwtToken;
            } else {
                log.error("Failed to get CA&A JWT: {}", sanitize(root.path("RespDesc").asText()));
                throw new RuntimeException("Failed to obtain CA&A JWT token");
            }
        } catch (org.springframework.web.client.HttpStatusCodeException httpEx) {
            log.error("HTTP Error from CA&A getting JWT: Status: {}, Body: {}", httpEx.getStatusCode(),
                    httpEx.getResponseBodyAsString(), httpEx);
            throw new RuntimeException("CA&A Integration Error", httpEx);
        } catch (Exception e) {
            log.error("Error communicating with CA&A for JWT token", e);
            throw new RuntimeException("CA&A Integration Error", e);
        }
    }

    public JsonNode authenticateAdUser(String msAccessToken) {
        try {
            String caaToken = getCaaJwtToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + caaToken); // or headers.setBearerAuth(caaToken);

            // Construct payload
            Map<String, Object> innerData = new HashMap<>();
            innerData.put("tenant_id", tenantId);
            innerData.put("client_id", clientId);
            innerData.put("access_token", msAccessToken);
            String innerJson = objectMapper.writeValueAsString(innerData);
            String base64InnerJson = Base64.getEncoder().encodeToString(innerJson.getBytes());

            Map<String, Object> reqParam = new HashMap<>();
            reqParam.put("k", "data");
            reqParam.put("v", base64InnerJson);

            Map<String, Object> body = new HashMap<>();
            body.put("function_id", "F100011");
            body.put("app_user", caaUsername);
            body.put("req_transaction_id", String.valueOf(System.currentTimeMillis()));
            body.put("state_name", "");
            body.put("req_parameters", List.of(reqParam));
            body.put("extra_xml", "");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("Authenticating AD User with CA&A at: {}/auth/ad", apiUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl + "/auth/ad",
                    HttpMethod.POST,
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            if (CAA_SUCCESS_CODE.equals(root.path("result_code").asText())) {
                JsonNode respParams = root.path("resp_parameters");
                for (JsonNode param : respParams) {
                    if (CAA_RESPONSE_DATA_KEY.equals(param.path("key").asText())) {
                        String base64Value = param.path("value").asText();
                        String jsonValue = new String(Base64.getDecoder().decode(base64Value));
                        return objectMapper.readTree(jsonValue); // Returns User Info + Role/Menu
                    }
                }
            } else {
                log.error("Failed to authenticate AD User with CA&A: {}", sanitize(root.path("result_desc").asText()));
                throw new RuntimeException("CA&A Auth Failed: " + root.path("result_desc").asText());
            }

        } catch (org.springframework.web.client.HttpStatusCodeException httpEx) {
            log.error("HTTP Error authenticating AD User with CA&A: Status: {}, Body: {}", httpEx.getStatusCode(),
                    httpEx.getResponseBodyAsString(), httpEx);
            throw new RuntimeException("CA&A Authentication Error", httpEx);
        } catch (Exception e) {
            log.error("Error authenticating AD User with CA&A: {}", e.getMessage(), e);
            throw new RuntimeException("CA&A Authentication Error", e);
        }
        return null;
    }
}
