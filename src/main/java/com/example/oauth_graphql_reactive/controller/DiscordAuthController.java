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
@RequestMapping("/oauth/discord")
public class DiscordAuthController {

    @Autowired
    UserService userService;

    @Autowired
    ModelMapper modelMapper;

    private final WebClient webClient = WebClient.create();

    @Value("${discord.oauth.client-id}")
    private String CLIENT_ID;

    @Value("${discord.oauth.client-secret}")
    private String CLIENT_SECRET;

    @Value("${discord.oauth.redirect-url}")
    private String REDIRECT_URL;

    @Value("${discord.oauth.state}")
    private String STATE;

    public String generateDiscordOAuthURL() {
        log.info("Generating Discord OAuth URL");
        String encodedScope = URLEncoder.encode("identify email", StandardCharsets.UTF_8);

        return String.format(
                "https://discord.com/api/oauth2/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s",
                CLIENT_ID, URLEncoder.encode(REDIRECT_URL, StandardCharsets.UTF_8), encodedScope, STATE);
    }

    private Mono<Map> getDiscordUser(String token) {
        return webClient.get()
                .uri("https://discord.com/api/users/@me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class);
    }

    @GetMapping("/callback")
    public Mono<Void> handleDiscordRedirect(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            ServerHttpResponse response) {

        log.info("Discord OAuth callback received");

        if (error != null || code == null) {
            log.error("OAuth error or missing code: {}", error);
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create("/error.html"));
            return response.setComplete();
        }

        return webClient.post()
                .uri("https://discord.com/api/oauth2/token")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(BodyInserters
                        .fromFormData("client_id", CLIENT_ID)
                        .with("client_secret", CLIENT_SECRET)
                        .with("grant_type", "authorization_code")
                        .with("code", code)
                        .with("redirect_uri", REDIRECT_URL)
                        .with("scope", "identify email"))

                .retrieve()
                .bodyToMono(String.class)
                .flatMap(tokenResponseStr -> {
                    log.info("Discord Token Response: {}", tokenResponseStr);
                    try {
                        Map<String, Object> tokenMap = new ObjectMapper().readValue(tokenResponseStr, Map.class);
                        String accessToken = (String) tokenMap.get("access_token");

                        return getDiscordUser(accessToken)
                                .flatMap(discordUser -> {
                                    User newUser = new User();
                                    newUser.setName((String) discordUser.get("username"));
                                    newUser.setPicture("https://cdn.discordapp.com/avatars/" +
                                            discordUser.get("id") + "/" + discordUser.get("avatar") + ".png");
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
