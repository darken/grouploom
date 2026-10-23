package io.grouploom.backend.eventtype;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.eventtype.dto.CreateEventTypeRequest;
import io.grouploom.backend.eventtype.dto.EventTypeResponse;
import io.grouploom.backend.eventtype.dto.UpdateEventTypeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/event-types", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
public class EventTypeController {

	private static final Set<String> SORTABLE = Set.of("name", "createdAt");

	private final EventTypeService service;

	public EventTypeController(EventTypeService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "List event types",
			description = "Sortable properties: name, createdAt (e.g. `sort=name,asc`). Page size is capped at 100.")
	public PagedModel<EventTypeResponse> list(@Parameter(hidden = true) CurrentUser current,
			@RequestParam(defaultValue = "false") boolean archived,
			@ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
		pageable.getSort().forEach(order -> {
			if (!SORTABLE.contains(order.getProperty())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Unsupported sort property: " + order.getProperty() + ". Use name or createdAt.");
			}
		});
		return new PagedModel<>(service.list(current, archived, pageable));
	}

	@GetMapping("/{id}")
	@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventTypeResponse.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public EventTypeResponse get(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id) {
		return service.get(current, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "409", description = "Name already taken (code event-type.name-taken)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<EventTypeResponse> create(@Parameter(hidden = true) CurrentUser current,
			@Valid @RequestBody CreateEventTypeRequest request) {
		EventTypeResponse created = service.create(current, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = EventTypeResponse.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409",
			description = "Name already taken (event-type.name-taken) or stale version (event-type.stale)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public EventTypeResponse update(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id,
			@Valid @RequestBody UpdateEventTypeRequest request) {
		return service.update(current, id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Used by events (code event-type.in-use); archive it instead",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public void delete(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id) {
		service.delete(current, id);
	}

}
