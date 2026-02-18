package com.project.drawguess.dto;

import lombok.Data;

@Data
public class RefreshResponseDto {
	public String accessToken;
	public long expiresIn;
}
