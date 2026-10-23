package io.grouploom.backend.event.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.ZoneId;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** The value must be an IANA zone id such as {@code Europe/Madrid}. Null passes; combine with @NotBlank. */
@Documented
@Constraint(validatedBy = ValidTimeZone.Validator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT })
@Retention(RUNTIME)
public @interface ValidTimeZone {

	String message() default "must be a valid time zone, e.g. Europe/Madrid";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ValidTimeZone, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || value.isBlank() || ZoneId.getAvailableZoneIds().contains(value);
		}

	}

}
