package io.grouploom.backend.error;

import java.util.List;

import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	public record FieldError(String field, String message) {
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		if (ex.getCode() != null) {
			problem.setProperty("code", ex.getCode());
		}
		if (ex.getField() != null) {
			problem.setProperty("field", ex.getField());
		}
		return problem;
	}

	// Same shape as Bean Validation errors (`errors[{field, message}]`), plus a stable `code`.
	@ExceptionHandler(BadRequestException.class)
	ProblemDetail handleBadRequest(BadRequestException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		if (ex.getCode() != null) {
			problem.setProperty("code", ex.getCode());
		}
		if (ex.getField() != null) {
			problem.setProperty("errors", List.of(new FieldError(ex.getField(), ex.getMessage())));
		}
		return problem;
	}

	// Safety net: sort parameters are whitelisted in controllers, but never leak a 500 for a bad one.
	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail handleInvalidSort(PropertyReferenceException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid sort property.");
	}

	@ExceptionHandler({ InvalidCredentialsException.class, InvalidRefreshTokenException.class })
	ProblemDetail handleUnauthorized(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler(InvalidMemberTokenException.class)
	ProblemDetail handleInvalidMemberToken(InvalidMemberTokenException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
		problem.setProperty("code", ex.getCode());
		return problem;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
			.map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
			.toList();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	// Constraints on @RequestParam / @PathVariable (e.g. @Size) surface here rather than as body errors.
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = ex.getParameterValidationResults().stream()
			.flatMap(result -> result.getResolvableErrors().stream()
				.map(e -> new FieldError(result.getMethodParameter().getParameterName(), e.getDefaultMessage())))
			.toList();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

}
