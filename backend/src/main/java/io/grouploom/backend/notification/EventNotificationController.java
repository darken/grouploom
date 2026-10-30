package io.grouploom.backend.notification;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.notification.dto.AudienceResponse;
import io.grouploom.backend.notification.dto.EventNotificationResponse;
import io.grouploom.backend.notification.dto.ScheduleNotificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/events/{eventId}/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
public class EventNotificationController {

	private static final String PROBLEM = "application/problem+json";

	private final EventNotificationService service;

	public EventNotificationController(EventNotificationService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "List an event's notifications",
			description = "Not paged on purpose: the domain bounds the list (at most 10 scheduled notifications, plus the "
					+ "ones already sent). Ordered by send time. Delivery counts come from the email outbox.")
	@ApiResponse(responseCode = "200", description = "OK",
			content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = EventNotificationResponse.class))))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public List<EventNotificationResponse> list(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID eventId) {
		return service.list(current, eventId);
	}

	@GetMapping("/audience")
	@Operation(summary = "Preview who a notification would reach",
			description = "`eligible` counts members who aren't paused and haven't opted out of the event's type. "
					+ "For `kind=REMINDER` it also leaves out members who said they can't go. "
					+ "The real recipients are chosen when the notification is sent.")
	@ApiResponse(responseCode = "200", description = "OK",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = AudienceResponse.class)))
	@ApiResponse(responseCode = "400", description = "Invalid kind (notification.invalid-kind for CANCELLATION)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public AudienceResponse audience(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID eventId,
			@Parameter(description = "INVITATION or REMINDER") @RequestParam(defaultValue = "INVITATION") NotificationKind kind) {
		return service.audience(current, eventId, kind);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Schedule a notification",
			description = "Sends an invitation or reminder now, a number of minutes before the start, or at a custom time.")
	@ApiResponse(responseCode = "201", description = "Created",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventNotificationResponse.class)))
	@ApiResponse(responseCode = "400",
			description = "Invalid input. Codes: notification.send-at-invalid (on offsetMinutes or localSendAt), notification.invalid-kind",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409",
			description = "Limit of 10 scheduled notifications (notification.limit), cancelled event (event.cancelled) or event already started (event.started)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<EventNotificationResponse> schedule(@Parameter(hidden = true) CurrentUser current,
			@PathVariable UUID eventId, @Valid @RequestBody ScheduleNotificationRequest request) {
		EventNotificationResponse created = service.schedule(current, eventId, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@DeleteMapping("/{notificationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Unschedule a notification", description = "Only while it is still scheduled.")
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Already sent (notification.already-sent)",
			content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
	public void unschedule(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID eventId,
			@PathVariable UUID notificationId) {
		service.unschedule(current, eventId, notificationId);
	}

}
