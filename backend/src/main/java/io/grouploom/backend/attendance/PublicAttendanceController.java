package io.grouploom.backend.attendance;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.grouploom.backend.attendance.dto.AnswerRequest;
import io.grouploom.backend.attendance.dto.PublicAttendanceResponse;
import io.grouploom.backend.error.InvalidMemberTokenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/**
 * Reached from the reply buttons in invitation and reminder emails, so it is {@code permitAll} at the URL level
 * and authorized by the attendance token in {@code X-Member-Token}. Loading the page never records an answer;
 * the member confirms with a {@code PUT}.
 */
@RestController
@RequestMapping(path = "/public/attendance", produces = MediaType.APPLICATION_JSON_VALUE)
public class PublicAttendanceController {

	static final String TOKEN_HEADER = "X-Member-Token";
	private static final String PROBLEM = "application/problem+json";

	private final PublicAttendanceService service;

	public PublicAttendanceController(PublicAttendanceService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "Get the event and a member's answer",
			description = "Authorized by the attendance link token, not by a user session. Never records an answer.")
	@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicAttendanceResponse.class)))
	@ApiResponse(responseCode = "401",
			description = "Invalid link (member-token.invalid) or expired link (member-token.expired)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public PublicAttendanceResponse get(
			@Parameter(name = TOKEN_HEADER, in = ParameterIn.HEADER, required = true,
					description = "The token from the member's link") @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
		return service.get(requireToken(token));
	}

	@PutMapping
	@Operation(summary = "Answer for an event",
			description = "Sets or changes the member's answer until the event starts. Replaces any earlier answer.")
	@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicAttendanceResponse.class)))
	@ApiResponse(responseCode = "401",
			description = "Invalid link (member-token.invalid) or expired link (member-token.expired)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "The event started or was cancelled (attendance.closed)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public PublicAttendanceResponse answer(
			@Parameter(name = TOKEN_HEADER, in = ParameterIn.HEADER, required = true,
					description = "The token from the member's link") @RequestHeader(value = TOKEN_HEADER, required = false) String token,
			@Valid @RequestBody AnswerRequest request) {
		return service.answer(requireToken(token), request);
	}

	// A missing header is a 401 like any other bad link (not a 400), so the page always shows the same message.
	private static String requireToken(String token) {
		if (token == null || token.isBlank()) {
			throw new InvalidMemberTokenException(InvalidMemberTokenException.INVALID);
		}
		return token;
	}

}
