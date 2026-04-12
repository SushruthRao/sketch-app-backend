package com.project.drawguess.dto;

import lombok.Data;

@Data
public class AuthResponseDto {
	public String userName;
	public String accessToken;
	public long expiresIn;
	public boolean isAdmin;
}