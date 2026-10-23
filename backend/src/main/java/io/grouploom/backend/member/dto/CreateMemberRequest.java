package io.grouploom.backend.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMemberRequest(
		@NotBlank @Size(max = 120) String name,
		// The regexp requires a dot in the domain, which @Email alone doesn't ("a@b" passes it).
		@NotBlank @Size(max = 254) @Email(regexp = ".+@.+\\..+") String email) {

	// Trim up front so padding from a client never fails @Email or counts toward the length limits.
	public CreateMemberRequest {
		name = name == null ? null : name.trim();
		email = email == null ? null : email.trim();
	}

}
