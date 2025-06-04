package com.example.oauth_graphql_reactive.controller;

import java.net.URI;
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
import org.springframework.web.reactive.function.client.WebClient;

import com.example.oauth_graphql_reactive.entity.User;
import com.example.oauth_graphql_reactive.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@RequestMapping("/oauth")
public class GoogleAuthController {

  @Autowired
  UserService userService;

  @Autowired
  ModelMapper modelMapper;

  private final WebClient webClient = WebClient.create();

  @Value("${google.oauth.client-id}")
  private String CLIENT_ID;

  @Value("${google.oauth.client-secret}")
  private String CLIENT_SECRET;

  @Value("${google.oauth.scope}")
  private String SCOPE;

  @Value("${google.oauth.redirect-url}")
  private String REDIRECT_URL;

  @Value("${google.oauth.state}")
  private String STATE;

  @GetMapping("/generate_url")
  public Mono<String> generateGoogleOAuthURL() {
    log.info("control inside GoogleAuthController.generateGoogleAuthURL()");

    String url = String.format(
        "https://accounts.google.com/o/oauth2/v2/auth?scope=%s&include_granted_scopes=true&response_type=code&redirect_uri=%s&client_id=%s&state=%s",
        SCOPE, REDIRECT_URL, CLIENT_ID, STATE);

    return Mono.just(url);
  }

  public Mono<Map> getUserInfo(String accessToken) {
    return webClient.get()
        .uri("https://www.googleapis.com/oauth2/v3/userinfo")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken) // now correct token
        .retrieve()
        .bodyToMono(Map.class);
  }

  @GetMapping("/redirect_url")
  public Mono<Void> handleAuthorizationCode(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      ServerHttpResponse response) {

    log.info("control inside GoogleAuthController.handleAuthorizationCode()");

    if (error != null) {
      log.error("Error during auth: {}", error);
      response.setStatusCode(HttpStatus.FOUND);
      response.getHeaders().setLocation(URI.create("/error.html"));
      return response.setComplete();
    }

    String bodyString = String.format(
        "code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code",
        code, CLIENT_ID, CLIENT_SECRET, REDIRECT_URL);

    return webClient.post()
        .uri("https://oauth2.googleapis.com/token")
        .bodyValue(bodyString)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .retrieve()
        .bodyToMono(String.class)
        .flatMap(tokenResponseStr -> {
          log.info("Token response: {}", tokenResponseStr);
          try {
            Map<String, Object> tokenMap = new ObjectMapper().readValue(tokenResponseStr, Map.class);
            String accessToken = (String) tokenMap.get("access_token");

            return getUserInfo(accessToken)
                .flatMap(userFromGoogle -> {
                  User newUser = new User();

                  newUser.setName((String) userFromGoogle.get("name"));
                  newUser.setPicture((String) userFromGoogle.get("picture"));
                  newUser.setResponseStr(tokenMap);

                  return userService.createUser(newUser);
                })
                .flatMap(userFromDB -> {
                  String redirectUrl = "/googleAfterAuth.html?id=" + userFromDB.getId();
                  response.setStatusCode(HttpStatus.FOUND);
                  response.getHeaders().setLocation(URI.create(redirectUrl));
                  return response.setComplete();
                });

          } catch (Exception e) {
            log.error("Failed to parse token response", e);
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create("/error.html"));
            return response.setComplete();
          }
        });
  }

}
