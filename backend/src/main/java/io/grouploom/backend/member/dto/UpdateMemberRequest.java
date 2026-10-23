package io.grouploom.backend.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(
		@NotBlank @Size(max = 120) String name,
		@NotBlank @Size(max = 254) @Email(regexp = ".+@.+\\..+") String email,
		@NotNull Long version) {

	// Trim up front so padding from a client never fails @Email or counts toward the length limits.
	public UpdateMemberRequest {
		name = name == null ? null : name.trim();
		email = email == null ? null : email.trim();
	}

}
