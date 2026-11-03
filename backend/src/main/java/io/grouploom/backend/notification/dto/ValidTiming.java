package io.grouploom.backend.notification.dto;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.grouploom.backend.notification.NotificationKind;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A schedule request must be coherent: {@code BEFORE_START} needs {@code offsetMinutes}, {@code AT} needs
 * {@code localSendAt}, {@code NOW} takes neither, and only invitations and reminders can be scheduled.
 */
@Documented
@Constraint(validatedBy = ValidTiming.Validator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidTiming {

	String message() default "{validation.timing.invalid}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ValidTiming, ScheduleNotificationRequest> {

		@Override
		public boolean isValid(ScheduleNotificationRequest r, ConstraintValidatorContext context) {
			if (r == null) {
				return true;
			}
			context.disableDefaultConstraintViolation();
			boolean valid = true;
			if (r.kind() == NotificationKind.CANCELLATION) {
				valid = violation(context, "kind", "{validation.timing.kind}");
			}
			if (r.timing() == null) {
				return valid;
			}
			switch (r.timing()) {
				case NOW -> {
					if (r.offsetMinutes() != null) {
						valid = violation(context, "offsetMinutes", "{validation.timing.not-allowed-now}");
					}
					if (r.localSendAt() != null) {
						valid = violation(context, "localSendAt", "{validation.timing.not-allowed-now}");
					}
				}
				case BEFORE_START -> {
					if (r.offsetMinutes() == null) {
						valid = violation(context, "offsetMinutes", "{validation.timing.required-before-start}");
					}
					if (r.localSendAt() != null) {
						valid = violation(context, "localSendAt", "{validation.timing.not-allowed-before-start}");
					}
				}
				case AT -> {
					if (r.localSendAt() == null) {
						valid = violation(context, "localSendAt", "{validation.timing.required-at}");
					}
					if (r.offsetMinutes() != null) {
						valid = violation(context, "offsetMinutes", "{validation.timing.not-allowed-at}");
					}
				}
			}
			return valid;
		}

		private static boolean violation(ConstraintValidatorContext context, String field, String message) {
			context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
			return false;
		}

	}

}
