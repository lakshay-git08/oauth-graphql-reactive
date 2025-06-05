package com.example.oauth_graphql_reactive.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.oauth_graphql_reactive.entity.User;
import com.example.oauth_graphql_reactive.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@RequestMapping("/oauth/meta")
public class MetaAuthController {

    @Autowired
    private UserService userService;

    private final WebClient webClient = WebClient.create();

    @Value("${meta.oauth.client-id}")
    private String CLIENT_ID;

    @Value("${meta.oauth.client-secret}")
    private String CLIENT_SECRET;

    @Value("${meta.oauth.redirect-url}")
    private String REDIRECT_URL;

    @Value("${meta.oauth.state}")
    private String STATE;

    @Value("${meta.oauth.scope}")
    private String SCOPE;

    public String generateMetaOAuthURL() {
        log.info("Generating Meta OAuth URL");

        String encodedScope = URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);
        return String.format(
                "https://www.facebook.com/v19.0/dialog/oauth?client_id=%s&redirect_uri=%s&state=%s&scope=%s",
                CLIENT_ID, REDIRECT_URL, STATE, encodedScope);
    }

    private Mono<Map> getMetaUser(String accessToken) {
        String fields = "id,name,email,picture";
        return webClient.get()
                .uri("https://graph.facebook.com/me?fields=" + fields)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(Map.class);
    }

    @GetMapping("/callback")
    public Mono<Void> handleMetaRedirect(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            ServerHttpResponse response) {

        log.info("Meta OAuth callback received");

        if (error != null || code == null) {
            log.error("OAuth error or missing code: {}", error);
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create("/error.html"));
            return response.setComplete();
        }

        return webClient.post()
                .uri("https://graph.facebook.com/v19.0/oauth/access_token")
                .body(BodyInserters
                        .fromFormData("client_id", CLIENT_ID)
                        .with("client_secret", CLIENT_SECRET)
                        .with("code", code)
                        .with("redirect_uri", REDIRECT_URL))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(tokenResponseStr -> {
                    log.info("Meta Token Response: {}", tokenResponseStr);
                    try {
                        Map<String, Object> tokenMap = new ObjectMapper().readValue(tokenResponseStr, Map.class);
                        String accessToken = (String) tokenMap.get("access_token");

                        return getMetaUser(accessToken)
                                .flatMap(userFromMeta -> {
                                    User newUser = new User();
                                    newUser.setName((String) userFromMeta.get("name"));
                                    newUser.setPicture(((Map) ((Map) userFromMeta.get("picture")).get("data"))
                                            .get("url").toString());
                                    newUser.setResponseStr(tokenMap);

                                    return userService.createUser(newUser);
                                })
                                .flatMap(savedUser -> {
                                    String redirectUrl = "/afterAuth.html?id=" + savedUser.getId();
                                    response.setStatusCode(HttpStatus.FOUND);
                                    response.getHeaders().setLocation(URI.create(redirectUrl));
                                    return response.setComplete();
                                });

                    } catch (Exception e) {
                        log.error("Token parsing error", e);
                        response.setStatusCode(HttpStatus.FOUND);
                        response.getHeaders().setLocation(URI.create("/error.html"));
                        return response.setComplete();
                    }
                });
    }
}
