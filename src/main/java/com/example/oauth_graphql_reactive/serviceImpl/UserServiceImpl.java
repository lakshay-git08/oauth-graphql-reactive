package com.example.oauth_graphql_reactive.serviceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.oauth_graphql_reactive.entity.User;
import com.example.oauth_graphql_reactive.repository.UserRepository;
import com.example.oauth_graphql_reactive.service.UserService;

import reactor.core.publisher.Mono;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    UserRepository userRepository;

    @Override
    public Mono<User> getUserById(String id) {
        return userRepository.findById(id);
    }

    @Override
    public Mono<User> createUser(User user) {
        return userRepository.save(user);
    }

}
