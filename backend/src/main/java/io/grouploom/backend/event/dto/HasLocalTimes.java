package io.grouploom.backend.event.dto;

import java.time.LocalDateTime;

/** A request carrying wall-clock start and end times, so one validator serves create and update. */
public interface HasLocalTimes {

	LocalDateTime localStart();

	LocalDateTime localEnd();

}
