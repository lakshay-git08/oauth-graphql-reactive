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
@RequestMapping("/oauth/github")
public class GithubAuthController {

    @Autowired
    UserService userService;

    @Autowired
    ModelMapper modelMapper;

    private final WebClient webClient = WebClient.create();

    @Value("${github.oauth.client-id}")
    private String CLIENT_ID;

    @Value("${github.oauth.client-secret}")
    private String CLIENT_SECRET;

    @Value("${github.oauth.redirect-url}")
    private String REDIRECT_URL;

    @Value("${github.oauth.state}")
    private String STATE;

    public String generateGitHubOAuthURL() {
        log.info("Generating GitHub OAuth URL");

        String encodedScope = URLEncoder.encode("read:user user:email", StandardCharsets.UTF_8);
        String url = String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&state=%s&scope=%s",
                CLIENT_ID, REDIRECT_URL, STATE, encodedScope);

        return url;
    }

    private Mono<Map> getGitHubUser(String token) {
        return webClient.get()
                .uri("https://api.github.com/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class);
    }

    @GetMapping("/callback")
    public Mono<Void> handleGitHubRedirect(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            ServerHttpResponse response) {
        log.info("GitHub OAuth callback received");

        if (error != null || code == null) {
            log.error("OAuth error or missing code: {}", error);
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create("/error.html"));
            return response.setComplete();
        }

        return webClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header(HttpHeaders.ACCEPT, "application/json")
                .body(BodyInserters
                        .fromFormData("client_id", CLIENT_ID)
                        .with("client_secret", CLIENT_SECRET)
                        .with("code", code)
                        .with("redirect_uri", REDIRECT_URL)
                        .with("state", state))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(tokenResponseStr -> {
                    log.info("GitHub Token Response: {}", tokenResponseStr);
                    try {
                        Map<String, Object> tokenMap = new ObjectMapper().readValue(tokenResponseStr, Map.class);
                        String accessToken = (String) tokenMap.get("access_token");

                        return getGitHubUser(accessToken)
                                .flatMap(userFromGitHub -> {
                                    User newUser = new User();
                                    newUser.setName((String) userFromGitHub.get("name"));
                                    newUser.setPicture((String) userFromGitHub.get("avatar_url"));
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
