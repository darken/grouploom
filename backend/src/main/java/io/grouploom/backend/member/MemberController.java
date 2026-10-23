package io.grouploom.backend.member;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.email.dto.BulkEmailResult;
import io.grouploom.backend.email.dto.EmailQueuedResponse;
import io.grouploom.backend.member.dto.CreateMemberRequest;
import io.grouploom.backend.member.dto.MemberResponse;
import io.grouploom.backend.member.dto.PreferencesLinkResponse;
import io.grouploom.backend.member.dto.UpdateMemberRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping(path = "/members", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

	private static final Set<String> SORTABLE = Set.of("name", "email", "createdAt");
	private static final Set<String> TEXT_SORTS = Set.of("name", "email");

	private final MemberService service;

	public MemberController(MemberService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(summary = "List members",
			description = "Optional `q` matches name or email (case-insensitive, max 100 chars). "
					+ "Sortable properties: name, email, createdAt (e.g. `sort=name,asc`). Page size is capped at 100.")
	public PagedModel<MemberResponse> list(@Parameter(hidden = true) CurrentUser current,
			@RequestParam(required = false) @Size(max = 100) String q,
			@ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
		return new PagedModel<>(service.list(current, q, validSort(pageable)));
	}

	@GetMapping("/{id}")
	@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberResponse.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public MemberResponse get(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id) {
		return service.get(current, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "409", description = "Email already taken (code member.email-taken)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<MemberResponse> create(@Parameter(hidden = true) CurrentUser current,
			@Valid @RequestBody CreateMemberRequest request) {
		MemberResponse created = service.create(current, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	@ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemberResponse.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409",
			description = "Email already taken (member.email-taken) or stale version (member.stale)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public MemberResponse update(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id,
			@Valid @RequestBody UpdateMemberRequest request) {
		return service.update(current, id, request);
	}

	@PostMapping("/{id}/preferences-link")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a personal preferences link for a member",
			description = "Each call issues a new link; earlier ones keep working until they expire or the member's email changes.")
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public PreferencesLinkResponse createPreferencesLink(@Parameter(hidden = true) CurrentUser current,
			@PathVariable UUID id) {
		return service.createPreferencesLink(current, id);
	}

	@PostMapping("/{id}/preferences-email")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Email a member their preferences link",
			description = "Queues the email; a background dispatcher sends it, so check `preferencesEmail` on the member for the outcome. "
					+ "Each call issues a new link when the email is sent.")
	@ApiResponse(responseCode = "202", description = "Queued", content = @Content(mediaType = "application/json", schema = @Schema(implementation = EmailQueuedResponse.class)))
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "429", description = "A link was sent to this member recently (code email.cooldown)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public EmailQueuedResponse sendPreferencesEmail(@Parameter(hidden = true) CurrentUser current,
			@PathVariable UUID id) {
		return service.sendPreferencesEmail(current, id);
	}

	@PostMapping("/preferences-email")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Email every member their preferences link",
			description = "Queues one email per member, skipping members emailed within the cooldown.")
	@ApiResponse(responseCode = "202", description = "Queued", content = @Content(mediaType = "application/json", schema = @Schema(implementation = BulkEmailResult.class)))
	public BulkEmailResult sendPreferencesEmailToAll(@Parameter(hidden = true) CurrentUser current) {
		return service.sendPreferencesEmailToAll(current);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "404", description = "Not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public void delete(@Parameter(hidden = true) CurrentUser current, @PathVariable UUID id) {
		service.delete(current, id);
	}

	/** Rejects unknown sort properties and makes name/email sorting case-insensitive. */
	private static Pageable validSort(Pageable pageable) {
		List<Sort.Order> orders = pageable.getSort().stream().map(order -> {
			if (!SORTABLE.contains(order.getProperty())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Unsupported sort property: " + order.getProperty() + ". Use name, email or createdAt.");
			}
			return TEXT_SORTS.contains(order.getProperty()) ? order.ignoreCase() : order;
		}).toList();
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
	}

}
