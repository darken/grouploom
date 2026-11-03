package io.grouploom.backend.error;

import java.util.List;

import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.databind.DatabindException;

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

	@ExceptionHandler(TooManyRequestsException.class)
	ProblemDetail handleTooManyRequests(TooManyRequestsException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
		problem.setProperty("code", ex.getCode());
		return problem;
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

	// A value Jackson can't read (e.g. an unsupported enum tag) is reported against its field, like a validation error.
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (ex.getCause() instanceof DatabindException cause && !cause.getPath().isEmpty()) {
			String field = cause.getPath().getLast().getPropertyName();
			if (field != null) {
				ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
				problem.setProperty("errors", List.of(new FieldError(field, "Invalid value.")));
				return handleExceptionInternal(ex, problem, headers, status, request);
			}
		}
		return super.handleHttpMessageNotReadable(ex, headers, status, request);
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
