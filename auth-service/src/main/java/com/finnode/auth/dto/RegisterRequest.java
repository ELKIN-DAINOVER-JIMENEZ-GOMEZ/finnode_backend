package com.finnode.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Size(max = 150) String fullName,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(min = 8, max = 255) String password
) {
}
