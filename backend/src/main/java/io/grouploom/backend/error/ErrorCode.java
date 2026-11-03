package io.grouploom.backend.error;

/**
 * Every error the API reports on purpose. {@link #code()} is the stable wire value the frontend reacts to;
 * {@link #messageKey()} names the user-facing text in {@code messages*.properties}. Two codes can share a wire
 * value when the frontend treats them alike but they need different wording.
 */
public enum ErrorCode {

	ACCOUNT_EMAIL_TAKEN("account.email-taken"),
	ATTENDANCE_CLOSED("attendance.closed"),
	ATTENDANCE_EVENT_CANCELLED("attendance.closed", "attendance.event-cancelled"),
	ACCESS_DENIED("auth.access-denied"),
	AUTH_INVALID_CREDENTIALS("auth.invalid-credentials"),
	AUTH_REQUIRED("auth.required"),
	AUTH_SESSION_EXPIRED("auth.session-expired"),
	EMAIL_COOLDOWN("email.cooldown"),
	EVENT_CANCELLED("event.cancelled"),
	EVENT_END_BEFORE_START("event.end-before-start"),
	EVENT_ENDED("event.ended"),
	EVENT_EVENT_TYPE_ARCHIVED("event.event-type-archived"),
	EVENT_EVENT_TYPE_NOT_FOUND("event.event-type-not-found"),
	EVENT_HAS_NOTIFICATIONS("event.has-notifications"),
	EVENT_INVALID_WHEN("event.invalid-when"),
	EVENT_NOT_FOUND("event.not-found"),
	EVENT_NOTIFICATION_AFTER_START("event.notification-after-start"),
	EVENT_STALE("event.stale"),
	EVENT_START_IN_PAST("event.start-in-past"),
	EVENT_STARTED("event.started"),
	EVENT_TOO_LONG("event.too-long"),
	EVENT_TYPE_IN_USE("event-type.in-use"),
	EVENT_TYPE_NAME_TAKEN("event-type.name-taken"),
	EVENT_TYPE_NOT_FOUND("event-type.not-found"),
	EVENT_TYPE_STALE("event-type.stale"),
	GROUP_NOT_FOUND("group.not-found"),
	GROUP_OWNER_ONLY("group.owner-only"),
	MEMBER_EMAIL_TAKEN("member.email-taken"),
	MEMBER_NOT_FOUND("member.not-found"),
	MEMBER_STALE("member.stale"),
	MEMBER_TOKEN_EXPIRED("member-token.expired"),
	MEMBER_TOKEN_INVALID("member-token.invalid"),
	NOTIFICATION_ALREADY_SENT("notification.already-sent"),
	NOTIFICATION_INVALID_KIND("notification.invalid-kind"),
	NOTIFICATION_INVALID_KIND_PREVIEW("notification.invalid-kind", "notification.invalid-kind-preview"),
	NOTIFICATION_LIMIT("notification.limit"),
	NOTIFICATION_NOT_FOUND("notification.not-found"),
	NOTIFICATION_SEND_AT_INVALID("notification.send-at-invalid"),
	PREFERENCE_UNKNOWN_EVENT_TYPE("preference.unknown-event-type"),
	USER_NOT_FOUND("user.not-found");

	private final String code;

	private final String messageKey;

	ErrorCode(String code) {
		this(code, code);
	}

	ErrorCode(String code, String messageName) {
		this.code = code;
		this.messageKey = "error." + messageName;
	}

	public String code() {
		return code;
	}

	public String messageKey() {
		return messageKey;
	}

}
