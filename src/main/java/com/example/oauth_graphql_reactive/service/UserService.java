package com.example.oauth_graphql_reactive.service;

import com.example.oauth_graphql_reactive.entity.User;

import reactor.core.publisher.Mono;

public interface UserService {

    public Mono<User> getUserById(String id);

    public Mono<User> createUser(User user);
}
