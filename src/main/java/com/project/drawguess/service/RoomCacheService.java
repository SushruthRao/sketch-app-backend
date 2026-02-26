package com.project.drawguess.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.project.drawguess.model.Room;
import com.project.drawguess.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

/**
 * Thin caching layer over RoomRepository.
 *
 * Kept as a separate bean so that all callers (in other beans) go through the
 * Spring proxy, making @Cacheable / @CacheEvict effective.
 */
@Service
@RequiredArgsConstructor
public class RoomCacheService {

    private final RoomRepository roomRepository;

    /**
     * Returns the Room for the given code, hitting Redis on subsequent calls.
     * TTL is configured in CacheConfig (5 minutes).
     */
    @Cacheable(value = "rooms", key = "#roomCode", unless = "#result == null")
    public Room findByRoomCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode).stream().findFirst().orElse(null);
    }

    /**
     * Saves the room and evicts the cached entry so callers always see the
     * latest status, host, etc.
     */
    @CacheEvict(value = "rooms", key = "#room.roomCode")
    public Room save(Room room) {
        return roomRepository.save(room);
    }
}
