package io.grouploom.backend.event.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** {@code localEnd} must be after {@code localStart} (wall-clock comparison); the error lands on {@code localEnd}. */
@Documented
@Constraint(validatedBy = ValidTimeRange.Validator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidTimeRange {

	String message() default "must be after the start";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ValidTimeRange, HasLocalTimes> {

		@Override
		public boolean isValid(HasLocalTimes value, ConstraintValidatorContext context) {
			if (value == null || value.localStart() == null || value.localEnd() == null
					|| value.localEnd().isAfter(value.localStart())) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				.addPropertyNode("localEnd")
				.addConstraintViolation();
			return false;
		}

	}

}
