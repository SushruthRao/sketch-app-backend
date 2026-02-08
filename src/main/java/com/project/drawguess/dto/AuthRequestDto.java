package com.project.drawguess.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequestDto {

	@NotBlank(message = "Email is required")
	@Email(message = "Please provide a valid email address")
	public String email;

	@NotBlank(message = "Password is required")
	public String password;
}
