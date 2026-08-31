package com.beatblock.automap.engine;

import com.beatblock.automap.camera.CameraPlanningContext;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.camera.CameraSubjectKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraDirectorTest {

	@Test
	void returnsEmptyWhenDisabled() {
		List<StructuralSection> sections = List.of(new StructuralSection(0, 8, SectionType.BUILD));
		assertTrue(CameraDirector.generate(sections, 120f, 60, AutoMapStyle.EDM, false).isEmpty());
		assertTrue(CameraDirector.generateShots(
			sections, new CameraPlanningContext(120f, 60, AutoMapStyle.EDM, List.of()), false).isEmpty());
	}

	@Test
	void buildSectionGeneratesPushInOnStageObject() {
		List<StructuralSection> sections = List.of(new StructuralSection(8, 16, SectionType.BUILD));
		CameraPlanningContext context = new CameraPlanningContext(120f, 60, AutoMapStyle.EDM, List.of("stage-main"));

		List<CameraShot> shots = CameraDirector.generateShots(sections, context, true);

		assertTrue(shots.stream().anyMatch(s -> s.movement() == CameraShotMovement.PUSH_IN));
		assertTrue(shots.stream().anyMatch(s ->
			s.subject().kind() == CameraSubjectKind.STAGE_OBJECT && "stage-main".equals(s.subject().refId())));
	}

	@Test
	void dropSectionGeneratesOrbitAndShakeWithSubject() {
		List<StructuralSection> sections = List.of(new StructuralSection(16, 24, SectionType.DROP));
		CameraPlanningContext context = new CameraPlanningContext(120f, 60, AutoMapStyle.EDM, List.of("stage-drop"));

		List<CameraShot> shots = CameraDirector.generateShots(sections, context, true);

		assertTrue(shots.stream().anyMatch(s -> s.movement() == CameraShotMovement.ORBIT));
		assertTrue(shots.stream().anyMatch(s -> s.movement() == CameraShotMovement.SHAKE));
		assertTrue(shots.stream().allMatch(s -> s.durationSeconds() > 0));
	}

	@Test
	void introUsesOverviewSubject() {
		List<StructuralSection> sections = List.of(new StructuralSection(0, 8, SectionType.INTRO));
		CameraPlanningContext context = new CameraPlanningContext(120f, 60, AutoMapStyle.EDM, List.of("stage-a"));

		List<CameraShot> shots = CameraDirector.generateShots(sections, context, true);

		assertTrue(shots.stream().anyMatch(s -> s.subject().kind() == CameraSubjectKind.ALL_STAGE_OBJECTS));
	}

	@Test
	void legacyGeneratePreservesActionEnum() {
		List<StructuralSection> sections = List.of(new StructuralSection(8, 16, SectionType.BUILD));
		CameraPlanningContext context = new CameraPlanningContext(120f, 60, AutoMapStyle.EDM, List.of("stage-a"));
		List<CameraEvent> events = CameraDirector.generateShots(sections, context, true).stream()
			.map(CameraEvent::new)
			.toList();
		assertTrue(events.stream().anyMatch(e -> e.getAction() == CameraAction.ZOOM_IN));
	}

	@Test
	void deduplicatesEventsAtSameTimestamp() {
		List<StructuralSection> sections = List.of(
			new StructuralSection(0, 4, SectionType.INTRO),
			new StructuralSection(4, 8, SectionType.VERSE)
		);
		CameraPlanningContext context = new CameraPlanningContext(120f, 60, AutoMapStyle.EDM, List.of());

		List<CameraShot> shots = CameraDirector.generateShots(sections, context, true);

		for (int i = 1; i < shots.size(); i++) {
			assertTrue(Math.abs(shots.get(i).startSeconds() - shots.get(i - 1).startSeconds()) >= 0.05);
		}
	}

	@Test
	void shotSummaryIncludesSubjectLabel() {
		List<StructuralSection> sections = List.of(new StructuralSection(16, 24, SectionType.DROP));
		CameraPlanningContext context = new CameraPlanningContext(120f, 60, AutoMapStyle.EDM, List.of("stage-a"));

		CameraShot orbit = CameraDirector.generateShots(sections, context, true).stream()
			.filter(s -> s.movement() == CameraShotMovement.ORBIT)
			.findFirst()
			.orElseThrow();

		assertEquals("ORBIT(StageObject stage-a)", orbit.summary());
	}
}
