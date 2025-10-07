package net.pautet.softs.demospring.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.config.SalesforceConfig;
import net.pautet.softs.demospring.entity.*;
import net.pautet.softs.demospring.repository.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Service
public class SalesforceAuthService {

    private final SalesforceConfig salesforceConfig;
    private SalesforceCredentials salesforceCredentials = new SalesforceCredentials();
    private final ObjectMapper objectMapper;
    private final MessageService messageService;

    public SalesforceAuthService(SalesforceConfig salesforceConfig, ObjectMapper objectMapper, MessageService messageService) {
        this.objectMapper = objectMapper;
        this.salesforceConfig = salesforceConfig;
        this.messageService = messageService;
    }

    public RestClient createDataCloudApiClient() throws IOException {
        if (salesforceConfig.privateKey() == null) {
            throw new IllegalStateException("Salesforce Data Cloud integration is disabled. SF_PRIVATE_KEY not configured.");
        }
        if (this.salesforceCredentials.datacloudTokenResponse() == null || this.salesforceCredentials.datacloudTokenResponse().accessToken() == null || this.salesforceCredentials.dataCloudAccessTokenExpiresAt() <= System.currentTimeMillis()) {
            log.info("Needs a new Data Cloud Access Token");
            getDataCloudToken();
        }
        return RestClient.builder().baseUrl(salesforceCredentials.dataCloudInstanceUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(this.salesforceCredentials.datacloudTokenResponse().accessToken()))
                .build();
    }

    public RestClient createSalesforceApiClient() throws IOException {
        if (salesforceConfig.privateKey() == null) {
            throw new IllegalStateException("Salesforce integration is disabled. SF_PRIVATE_KEY not configured.");
        }
        if (salesforceCredentials.salesforceApiTokenResponse() == null) {
            log.info("No Salesforce Access Token yet, getting one...");
            getSalesforceToken();
        } else if (salesforceCredentials.salesforceAccessTokenExpiresAt() <= System.currentTimeMillis()) {
            log.info("Needs a new Salesforce Access Token...");
            getSalesforceToken();
        }
        return RestClient.builder().baseUrl(salesforceCredentials.salesforceApiTokenResponse().instanceUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(this.salesforceCredentials.salesforceApiTokenResponse().accessToken()))
                .build();
    }

    public RestClient createSalesforceIdClient() throws IOException {
        if (salesforceConfig.privateKey() == null) {
            throw new IllegalStateException("Salesforce integration is disabled. SF_PRIVATE_KEY not configured.");
        }
        if (this.salesforceCredentials.salesforceApiTokenResponse() == null || this.salesforceCredentials.salesforceAccessTokenExpiresAt() <= System.currentTimeMillis()) {
            log.info("Needs a new Salesforce Token");
            getSalesforceToken();
        }
        if (salesforceCredentials.salesforceApiTokenResponse().id() == null) {
            throw new IllegalStateException("No salesforce user id !");
        }
        return RestClient.builder().baseUrl(salesforceCredentials.salesforceApiTokenResponse().instanceUrl())
                .defaultHeaders(headers -> headers.setBearerAuth(this.salesforceCredentials.salesforceApiTokenResponse().accessToken()))
                .build();
    }

    private void getSalesforceToken() throws IOException {
        salesforceConfig.validateSalesforceConfig();
        // salesforce token expiration is defined by the session level parameter in
        // salesforce configuration and there is no way to get a true expiration date for the token
        // usual default is 2 hours
        long salesforceTokenExpiresAt = System.currentTimeMillis() + salesforceConfig.sessionTimeout() * 1000;

        String jwt = Jwts.builder()
                .issuer(salesforceConfig.clientId())
                .subject(salesforceConfig.username())
                .claim("aud", salesforceConfig.loginUrl())
                .expiration(new Date(System.currentTimeMillis() + 5 * 60 * 1000)) // 5 mins
                .signWith(salesforceConfig.privateKey())
                .compact();

        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        formData.add("assertion", jwt);
        // Retrieve raw response first for better diagnostics
        ResponseEntity<byte[]> postResponse = RestClient.builder().baseUrl(salesforceConfig.loginUrl())
                .build().post().uri("/services/oauth2/token").body(formData)
                .retrieve().onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    byte[] bodyBytes = response.getBody().readAllBytes();
                    // If 400 Bad Request, try to deserialize into SalesforceApiError
                    if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
                        try {
                            SalesforceApiError apiError = objectMapper.readValue(bodyBytes, SalesforceApiError.class);
                            throw new IOException("Getting Salesforce Token invalid request (400): " + apiError.error() + " - " + apiError.errorDescription());
                        } catch (Exception ignore) {
                            // Fallback to plain string below
                        }
                    }
                    // Fallback: include raw body as UTF-8 string
                    String errorBody = new String(bodyBytes, StandardCharsets.UTF_8);
                    throw new IOException("Getting Salesforce Token failed with status " + response.getStatusCode() + ": " + response.getStatusText() + " : " + errorBody);
                }).toEntity(byte[].class);

        log.debug("Salesforce token: status={} headers={}", postResponse.getStatusCode(), postResponse.getHeaders());
        log.debug("Salesforce token: raw body={}", postResponse.getBody() != null ? new String(postResponse.getBody()) : "<null>");

        try {
            SalesforceTokenResponse tokenResponse = objectMapper.readValue(postResponse.getBody(), SalesforceTokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                throw new IOException("Unexpected TokenResponse for Salesforce token: " + tokenResponse);
            } else {
                log.warn("Salesforce access token response: {} ", tokenResponse);
                messageService.info("Got new Salesforce API token.");
                this.salesforceCredentials = new SalesforceCredentials(this.salesforceCredentials, salesforceTokenExpiresAt, tokenResponse);
                //getSalesforceUser();
            }
        } catch (UnrecognizedPropertyException upe) {
            log.error("Salesforce Token response contains an unrecognized field: ", upe);
            throw upe;
        } catch (MismatchedInputException mie) {
            log.error("Salesforce Token response mismatch: ", mie);
            throw mie;
        } catch (JsonParseException jpe) {
            log.error("Salesforce Access Token JSON is invalid!", jpe);
            throw jpe;
        }
    }

    private void getDataCloudToken() throws IOException {
        RestClient apiClient = createSalesforceApiClient();
        if (salesforceCredentials.salesforceApiTokenResponse() == null) {
            throw new IllegalStateException("No salesforce access token to get data cloud token !");
        }
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "urn:salesforce:grant-type:external:cdp");
        formData.add("subject_token", salesforceCredentials.salesforceApiTokenResponse().accessToken());
        formData.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        ResponseEntity<String> postResponse = apiClient.post().uri("/services/a360/token")
                .body(formData).retrieve()
                .onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    // For any other status, throw an exception with the response body as a utf-8 string
                    String errorBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new IOException("Getting Data Cloud Token failed with status " + response.getStatusCode() + ": " + response.getStatusText() + " : " + errorBody);
                })
                .toEntity(String.class);

        log.debug("getDataCloudToken response: {}", postResponse.getBody());
        // Get the Content-Type header
        MediaType contentType = postResponse.getHeaders().getContentType();

        // Handle based on Content-Type
        if (contentType != null && contentType.includes(MediaType.APPLICATION_JSON)) {
            DatacloudTokenResponse tokenResponse = objectMapper.readValue(postResponse.getBody(), DatacloudTokenResponse.class);
            if (tokenResponse.accessToken() == null || tokenResponse.expiresIn() == null) {
                throw new IOException("Unexpected Data Cloud access token response: " + tokenResponse);
            }
            log.warn("Data Cloud Token: {}", tokenResponse);
            messageService.info("Got new DataCloud token.");
            this.salesforceCredentials = new SalesforceCredentials(salesforceCredentials, tokenResponse);
        } else if (contentType != null && contentType.includes(MediaType.TEXT_HTML)) {
            // Salesforce token is likely invalid now
            this.salesforceCredentials = new SalesforceCredentials();
            throw new IOException("Unexpected Data Cloud access token response:  " + postResponse.getBody());
        } else {
            throw new IOException("Unexpected Data Cloud access token response, content-type: " + contentType);
        }
    }
}
