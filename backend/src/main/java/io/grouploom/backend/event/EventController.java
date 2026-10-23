package io.grouploom.backend.event;

import java.net.URI;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.BadRequestException;
import io.grouploom.backend.event.dto.CancelEventRequest;
import io.grouploom.backend.event.dto.CreateEventRequest;
import io.grouploom.backend.event.dto.EventResponse;
import io.grouploom.backend.event.dto.UpdateEventRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/events", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
public class EventController {

	private static final String BAD_REQUEST_CODES = "Invalid input. Codes: event.start-in-past, event.too-long, "
			+ "event.end-before-start, event.event-type-archived, event.event-type-not-found, "
			+ "event.notification-after-start (a scheduled absolute notification would fall after the new start)";

	private final EventService service;

	public EventController(EventService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "List events",
			description = "`when=upcoming` (default) lists events that haven't ended, soonest first; `when=past` lists ended "
					+ "events, most recent first. The order is fixed, so `sort` is ignored. Page size is capped at 100.")
	public PagedModel<EventResponse> list(@Parameter(hidden = true) CurrentUser current,
			@RequestParam(defaultValue = "upcoming") @Schema(allowableValues = { "upcoming", "past" }) String when,
			@RequestParam(required = false) UUID eventTypeId,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
		When parsed = When.parse(when);
		if (parsed == null) {
			throw new BadRequestException("when must be upcoming or past.", "event.invalid-when", "when");
		}
		Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		return new PagedModel<>(service.list(current, parsed, eventTypeId, unsorted));
	}

	@GetMapping("/{id}")
	@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventResponse.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public EventResponse get(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id) {
		return service.get(current, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "400", description = BAD_REQUEST_CODES,
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<EventResponse> create(@Parameter(hidden = true) CurrentUser current,
			@Valid @RequestBody CreateEventRequest request) {
		EventResponse created = service.create(current, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventResponse.class)))
	@ApiResponse(responseCode = "400", description = BAD_REQUEST_CODES,
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Stale version (event.stale) or cancelled event (event.cancelled)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public EventResponse update(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id,
			@Valid @RequestBody UpdateEventRequest request) {
		return service.update(current, id, request);
	}

	@PostMapping("/{id}/cancel")
	@Operation(summary = "Cancel an event",
			description = "Keeps the event visible as cancelled. Scheduled notifications are dropped and members who were "
					+ "already notified get a cancellation email. There is no un-cancel.")
	@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventResponse.class)))
	@ApiResponse(responseCode = "400", description = "Invalid input",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Already cancelled (event.cancelled) or already ended (event.ended)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public EventResponse cancel(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id,
			@Valid @RequestBody(required = false) CancelEventRequest request) {
		return service.cancel(current, id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "409", description = "Members were already notified (event.has-notifications); cancel the event instead",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public void delete(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id) {
		service.delete(current, id);
	}

}
