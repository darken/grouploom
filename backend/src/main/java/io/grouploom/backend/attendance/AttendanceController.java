package io.grouploom.backend.attendance;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.grouploom.backend.attendance.dto.AttendanceRowResponse;
import io.grouploom.backend.attendance.dto.AttendanceSummaryResponse;
import io.grouploom.backend.attendance.dto.SetAttendanceRequest;
import io.grouploom.backend.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/events/{eventId}/attendance", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
public class AttendanceController {

	private static final String PROBLEM = "application/problem+json";

	private final AttendanceService service;

	public AttendanceController(AttendanceService service) {
		this.service = service;
	}

	@GetMapping("/summary")
	@Operation(summary = "Count who is coming",
			description = "`noReply` counts current members without an answer; answers leave with removed members.")
	@ApiResponse(responseCode = "200", description = "OK",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = AttendanceSummaryResponse.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public AttendanceSummaryResponse summary(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID eventId) {
		return service.summary(current, eventId);
	}

	@GetMapping
	@Operation(summary = "List every member's answer",
			description = "All current members, ordered by name (not sortable), with their answer if any. "
					+ "`filter` narrows to one answer or to members who haven't replied. Page size is capped at 100.")
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public PagedModel<AttendanceRowResponse> list(@Parameter(hidden = true) CurrentUser current,
			@PathVariable UUID eventId, @RequestParam(defaultValue = "ALL") AttendanceFilter filter,
			@ParameterObject @PageableDefault(size = 50) Pageable pageable) {
		// The order is fixed by name, so a client-supplied sort is dropped.
		return new PagedModel<>(service.list(current, eventId, filter,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())));
	}

	@PutMapping("/{memberId}")
	@Operation(summary = "Record a member's answer",
			description = "Sets the answer on the member's behalf, until the event is cancelled (also after it starts). "
					+ "A later answer from the member replaces it.")
	@ApiResponse(responseCode = "200", description = "OK",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = AttendanceRowResponse.class)))
	@ApiResponse(responseCode = "404", description = "Event or member not found", content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "The event is cancelled (attendance.closed)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public AttendanceRowResponse set(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID eventId,
			@PathVariable UUID memberId, @Valid @RequestBody SetAttendanceRequest request) {
		return service.set(current, eventId, memberId, request);
	}

	@DeleteMapping("/{memberId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Clear a member's answer", description = "Succeeds when the member has no answer.")
	@ApiResponse(responseCode = "404", description = "Event or member not found", content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "The event is cancelled (attendance.closed)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public void clear(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID eventId, @PathVariable UUID memberId) {
		service.clear(current, eventId, memberId);
	}

}
