package com.example.oauth_graphql_reactive.controller;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@Slf4j
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    GithubAuthController githubAuthController;

    @Autowired
    GoogleAuthController googleAuthController;

    @Autowired
    MetaAuthController metaAuthController;

    @GetMapping("/callback/{type}")
    public Mono<Void> generateRedirectURL(@PathVariable String type, ServerHttpResponse response) {

        String redirectUrl = "/";

        if ("google".equals(type)) {
            redirectUrl = googleAuthController.generateGoogleOAuthURL();
        } else if ("github".equals(type)) {
            redirectUrl = githubAuthController.generateGitHubOAuthURL();
        } else if ("meta".equals(type)) {
            redirectUrl = metaAuthController.generateMetaOAuthURL();
        }

        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(redirectUrl));
        return response.setComplete();
    }
}
