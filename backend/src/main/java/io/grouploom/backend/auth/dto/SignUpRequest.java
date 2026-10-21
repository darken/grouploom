package io.grouploom.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
		@NotBlank @Size(max = 120) String groupName,
		@NotBlank @Size(max = 120) String displayName,
		@NotBlank @Email @Size(max = 254) String email,
		@NotNull @Size(min = 12, max = 128) String password) {
}
