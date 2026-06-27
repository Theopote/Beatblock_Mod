package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraDirectorTest {

	@Test
	void returnsEmptyWhenDisabled() {
		List<StructuralSection> sections = List.of(new StructuralSection(0, 8, SectionType.BUILD));
		assertTrue(CameraDirector.generate(sections, 120f, 60, AutoMapStyle.EDM, false).isEmpty());
	}

	@Test
	void buildSectionGeneratesZoomInEvent() {
		List<StructuralSection> sections = List.of(new StructuralSection(8, 16, SectionType.BUILD));

		List<CameraEvent> events = CameraDirector.generate(sections, 120f, 60, AutoMapStyle.EDM, true);

		assertTrue(events.stream().anyMatch(e -> e.getAction() == CameraAction.ZOOM_IN));
	}

	@Test
	void dropSectionGeneratesOrbitAndShake() {
		List<StructuralSection> sections = List.of(new StructuralSection(16, 24, SectionType.DROP));

		List<CameraEvent> events = CameraDirector.generate(sections, 120f, 60, AutoMapStyle.EDM, true);

		assertTrue(events.stream().anyMatch(e -> e.getAction() == CameraAction.ORBIT));
		assertTrue(events.stream().anyMatch(e -> e.getAction() == CameraAction.SHAKE));
	}

	@Test
	void deduplicatesEventsAtSameTimestamp() {
		List<StructuralSection> sections = List.of(
			new StructuralSection(0, 4, SectionType.INTRO),
			new StructuralSection(4, 8, SectionType.VERSE)
		);

		List<CameraEvent> events = CameraDirector.generate(sections, 120f, 60, AutoMapStyle.EDM, true);

		for (int i = 1; i < events.size(); i++) {
			assertTrue(Math.abs(events.get(i).getTimeSeconds() - events.get(i - 1).getTimeSeconds()) >= 0.05);
		}
	}
}
