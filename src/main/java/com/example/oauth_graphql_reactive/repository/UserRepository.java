package com.example.oauth_graphql_reactive.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.example.oauth_graphql_reactive.entity.User;

@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {

}
