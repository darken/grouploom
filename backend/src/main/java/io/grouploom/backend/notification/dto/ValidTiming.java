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

	String message() default "does not match the timing";

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
				valid = violation(context, "kind", "must be INVITATION or REMINDER");
			}
			if (r.timing() == null) {
				return valid;
			}
			switch (r.timing()) {
				case NOW -> {
					if (r.offsetMinutes() != null) {
						valid = violation(context, "offsetMinutes", "isn't allowed when sending now");
					}
					if (r.localSendAt() != null) {
						valid = violation(context, "localSendAt", "isn't allowed when sending now");
					}
				}
				case BEFORE_START -> {
					if (r.offsetMinutes() == null) {
						valid = violation(context, "offsetMinutes", "is required for BEFORE_START");
					}
					if (r.localSendAt() != null) {
						valid = violation(context, "localSendAt", "isn't allowed for BEFORE_START");
					}
				}
				case AT -> {
					if (r.localSendAt() == null) {
						valid = violation(context, "localSendAt", "is required for AT");
					}
					if (r.offsetMinutes() != null) {
						valid = violation(context, "offsetMinutes", "isn't allowed for AT");
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
