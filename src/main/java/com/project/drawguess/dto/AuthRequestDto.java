package com.project.drawguess.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for {@code POST /user/login}.
 *
 * Implemented as an immutable Java 21 record — Jackson binds to the
 * canonical constructor via component names. Bean Validation annotations on
 * the record components are enforced by {@code @Valid} at the controller.
 */
public record AuthRequestDto(

		@NotBlank(message = "Email is required")
		@Email(message = "Please provide a valid email address")
		String email,

		@NotBlank(message = "Password is required")
		String password
) {
}
