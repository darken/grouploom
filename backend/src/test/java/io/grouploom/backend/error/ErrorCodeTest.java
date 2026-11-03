package io.grouploom.backend.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import io.grouploom.backend.config.MessageBundleParityTest;

class ErrorCodeTest {

	@Test
	void everyErrorCode_hasAMessageInBothBundles() throws IOException {
		Properties english = MessageBundleParityTest.load("messages.properties");
		Properties japanese = MessageBundleParityTest.load("messages_ja.properties");

		for (ErrorCode code : ErrorCode.values()) {
			assertThat(english.getProperty(code.messageKey())).as("en " + code).isNotBlank();
			assertThat(japanese.getProperty(code.messageKey())).as("ja " + code).isNotBlank();
		}
	}

	@Test
	void messageKeys_areUnique() {
		assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::messageKey)).doesNotHaveDuplicates();
	}

	@Test
	void japaneseMessages_areNotLeftInEnglish() throws IOException {
		Properties english = MessageBundleParityTest.load("messages.properties");
		Properties japanese = MessageBundleParityTest.load("messages_ja.properties");

		for (ErrorCode code : ErrorCode.values()) {
			assertThat(japanese.getProperty(code.messageKey())).as("ja " + code)
				.isNotEqualTo(english.getProperty(code.messageKey()));
		}
	}

	@Test
	void messageKey_isDerivedFromTheWireCodeUnlessGivenExplicitly() {
		assertThat(ErrorCode.EVENT_STALE.code()).isEqualTo("event.stale");
		assertThat(ErrorCode.EVENT_STALE.messageKey()).isEqualTo("error.event.stale");
		// Two messages share one wire code the frontend reacts to.
		assertThat(ErrorCode.ATTENDANCE_EVENT_CANCELLED.code()).isEqualTo("attendance.closed");
		assertThat(ErrorCode.ATTENDANCE_EVENT_CANCELLED.messageKey()).isNotEqualTo(ErrorCode.ATTENDANCE_CLOSED.messageKey());
	}

}
