package com.project.drawguess.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.project.drawguess.model.User;
import com.project.drawguess.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final UserRepository userRepository;

    @Cacheable(value = "users", key = "#email", unless = "#result == null")
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Saves the user and evicts the cached entry so stale data is never served.
     */
    @CacheEvict(value = "users", key = "#user.email")
    public User save(User user) {
        return userRepository.save(user);
    }
}