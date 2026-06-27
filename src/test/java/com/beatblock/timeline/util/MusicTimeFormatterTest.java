package com.beatblock.timeline.util;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.ui.i18n.BBTexts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithBeatBlockContext
class MusicTimeFormatterTest {

	@Test
	void formatMmSsPadsSeconds() {
		assertEquals("0:00", MusicTimeFormatter.formatMmSs(0));
		assertEquals("1:03", MusicTimeFormatter.formatMmSs(63.7));
		assertEquals("62:03", MusicTimeFormatter.formatMmSs(3723));
	}

	@Test
	void formatMmSsFractionIncludesTenths() {
		assertEquals("1:03.7", MusicTimeFormatter.formatMmSsFraction(63.7));
	}

	@Test
	void formatBarBeatAt120Bpm() {
		assertEquals(BBTexts.get("beatblock.timeline.time.bar_beat", 1, 1), MusicTimeFormatter.formatBarBeat(0, 120));
		assertEquals(BBTexts.get("beatblock.timeline.time.bar_beat", 2, 1), MusicTimeFormatter.formatBarBeat(2.0, 120));
		assertEquals(BBTexts.get("beatblock.timeline.time.bar_beat", 2, 2), MusicTimeFormatter.formatBarBeat(2.5, 120));
		assertEquals("", MusicTimeFormatter.formatBarBeat(1.0, 0));
	}

	@Test
	void formatPositionDisplayWithAndWithoutBpm() {
		assertEquals(
			BBTexts.get(
				"beatblock.timeline.time.position",
				"1:00",
				"3:00",
				BBTexts.get("beatblock.timeline.time.bar_beat", 31, 1)
			),
			MusicTimeFormatter.formatPositionDisplay(60, 180, 120)
		);
		assertEquals(
			BBTexts.get("beatblock.timeline.time.position_no_bpm", "1:00", "3:00"),
			MusicTimeFormatter.formatPositionDisplay(60, 180, 0)
		);
	}

	@Test
	void barAndBeatNumbers() {
		assertEquals(2, MusicTimeFormatter.barNumber(2.0, 120));
		assertEquals(2, MusicTimeFormatter.beatNumber(2.5, 120));
		assertEquals(0, MusicTimeFormatter.barNumber(1.0, -1));
	}
}
