package io.grouploom.backend.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.user.dto.MeResponse;
import io.grouploom.backend.user.repository.UserRepository;

@Service
public class CurrentUserService {

	private final UserRepository users;

	public CurrentUserService(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public MeResponse me(CurrentUser current) {
		return users.findWithGroupById(current.userId())
			.filter(user -> user.getGroup().getId().equals(current.groupId()))
			.map(MeResponse::from)
			.orElseThrow(() -> new NotFoundException("User not found."));
	}

}
