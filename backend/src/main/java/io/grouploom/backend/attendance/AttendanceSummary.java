package io.grouploom.backend.attendance;

/** Projection of the answer counts of one event next to the group's current member count. */
public interface AttendanceSummary {

	long getGoing();

	long getNotGoing();

	long getMembers();

	/** Members without an answer; answers of removed members are gone, so this never goes negative. */
	default long getNoReply() {
		return getMembers() - getGoing() - getNotGoing();
	}

}
