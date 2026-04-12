package com.project.drawguess.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /user/register}.
 *
 * Note: {@code passwordHash} is a misleading field name — at this point the
 * value is still plain text. It is hashed with BCrypt inside
 * {@code UserServiceImpl#registerUser} before being persisted. Renaming
 * would break the frontend contract, so the misnomer is preserved with
 * this note.
 */
public record RegisterRequestDto(

		@NotNull
		@NotBlank(message = "Username cannot be blank")
		@Size(max = 24, message = "Username must be 24 characters or less")
		@Pattern(regexp = "^[a-zA-Z0-9_ ]+$",
				message = "Username can only contain letters, numbers, underscores and spaces")
		String username,

		@NotBlank(message = "Email is required")
		@Email(message = "Please provide a valid email address")
		String email,

		@NotBlank(message = "Password is required")
		@Size(min = 8, message = "Password must be at least 8 characters long")
		@Pattern(
				regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$",
				message = "Password must contain at least one digit, one lowercase and one uppercase letter, and one special character")
		String passwordHash
) {
}
