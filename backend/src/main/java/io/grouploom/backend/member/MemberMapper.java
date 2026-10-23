package io.grouploom.backend.member;

import io.grouploom.backend.member.dto.MemberResponse;

public final class MemberMapper {

	private MemberMapper() {
	}

	public static MemberResponse toResponse(Member m, boolean invitationsPaused) {
		return new MemberResponse(m.getId(), m.getName(), m.getEmail(), m.getVersion(), m.getCreatedAt(),
				m.getUpdatedAt(), invitationsPaused);
	}

}
