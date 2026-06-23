package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraDirectorTest {

	@Test
	void returnsEmptyWhenDisabled() {
		List<MusicSection> sections = List.of(new MusicSection(0, 8, SectionType.BUILD));
		assertTrue(CameraDirector.generate(sections, 120f, 60, AutoMapStyle.EDM, false).isEmpty());
	}

	@Test
	void buildSectionGeneratesZoomInEvent() {
		List<MusicSection> sections = List.of(new MusicSection(8, 16, SectionType.BUILD));

		List<CameraEvent> events = CameraDirector.generate(sections, 120f, 60, AutoMapStyle.EDM, true);

		assertTrue(events.stream().anyMatch(e -> e.getAction() == CameraAction.ZOOM_IN));
	}

	@Test
	void dropSectionGeneratesOrbitAndShake() {
		List<MusicSection> sections = List.of(new MusicSection(16, 24, SectionType.DROP));

		List<CameraEvent> events = CameraDirector.generate(sections, 120f, 60, AutoMapStyle.EDM, true);

		assertTrue(events.stream().anyMatch(e -> e.getAction() == CameraAction.ORBIT));
		assertTrue(events.stream().anyMatch(e -> e.getAction() == CameraAction.SHAKE));
	}

	@Test
	void deduplicatesEventsAtSameTimestamp() {
		List<MusicSection> sections = List.of(
			new MusicSection(0, 4, SectionType.INTRO),
			new MusicSection(4, 8, SectionType.VERSE)
		);

		List<CameraEvent> events = CameraDirector.generate(sections, 120f, 60, AutoMapStyle.EDM, true);

		for (int i = 1; i < events.size(); i++) {
			assertTrue(Math.abs(events.get(i).getTimeSeconds() - events.get(i - 1).getTimeSeconds()) >= 0.05);
		}
	}
}
