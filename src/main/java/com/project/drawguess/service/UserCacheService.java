package com.project.drawguess.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.project.drawguess.model.User;
import com.project.drawguess.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Thin caching layer over UserRepository.
 *
 * Kept as a separate bean so that callers in other beans go through the Spring
 * proxy — which is required for @Cacheable to intercept the call. Self-calls
 * (this.method()) bypass the proxy and would skip the cache entirely.
 */
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final UserRepository userRepository;

    /**
     * Returns the User for the given email, hitting Redis on subsequent calls.
     * TTL is configured in CacheConfig (30 minutes).
     */
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
