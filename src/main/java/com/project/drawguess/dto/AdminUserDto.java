package com.project.drawguess.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Lightweight user projection for the admin search panel.
// Deliberately excludes passwordHash — never expose that through any API.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserDto {
    private Long userId;
    private String username;
    private String email;
    private boolean isAdmin;
    private LocalDateTime createdAt;
}
