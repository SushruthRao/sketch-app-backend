package com.project.drawguess.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.project.drawguess.model.Room;
import com.project.drawguess.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomCacheService {

    private final RoomRepository roomRepository;

    // caching frequently used rooms
    
    @Cacheable(value = "rooms", key = "#roomCode", unless = "#result == null")
    public Room findByRoomCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode).stream().findFirst().orElse(null);
    }

    // evicting cache after ttl is over (ttl is in cacheconfig, its 5 mins)
    
    @CachePut(value = "rooms", key = "#room.roomCode")
    public Room save(Room room) {
        return roomRepository.save(room);
    }
}