package com.beatblock.automap.choreography;

import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotFraming;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.camera.CameraSubjectKind;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.engine.BuildSequenceMode;
import com.beatblock.timeline.generation.PacingMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildRevealCameraPlannerTest {

	@Test
	void planShotsFollowBuildProgressArc() {
		BuildSequencePlan sequence = new BuildSequencePlan(
			"layer-1", "stage-1", 8.0, 18.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false, null, 1
		);

		List<CameraShot> shots = BuildRevealCameraPlanner.planShots(sequence);

		assertEquals(5, shots.size());
		assertEquals(CameraShotMovement.HOLD, shots.get(0).movement());
		assertEquals(CameraShotFraming.OVERVIEW, shots.get(0).framing());
		assertEquals(CameraSubjectKind.ALL_STAGE_OBJECTS, shots.get(0).subject().kind());

		assertEquals(CameraShotMovement.PAN, shots.get(1).movement());
		assertEquals(CameraShotMovement.ORBIT, shots.get(2).movement());
		assertEquals(CameraSubjectKind.BUILD_LAYER, shots.get(2).subject().kind());
		assertEquals("layer-1", shots.get(2).subject().refId());

		assertEquals(CameraShotMovement.PUSH_IN, shots.get(3).movement());
		assertEquals(CameraShotFraming.CLOSE, shots.get(3).framing());

		assertEquals(CameraShotMovement.HOLD, shots.get(4).movement());
		assertEquals(CameraShotFraming.CLOSE, shots.get(4).framing());

		assertEquals(8.0, shots.get(0).startSeconds(), 1e-6);
		assertTrue(shots.get(2).startSeconds() >= 8.0 + 10.0 * 0.29);
		assertTrue(shots.get(3).startSeconds() >= 8.0 + 10.0 * 0.69);
		assertTrue(shots.get(4).startSeconds() >= 8.0 + 10.0 * 0.94);
	}

	@Test
	void applyReplacesOverlappingTemplateCameras() {
		BuildSequencePlan sequence = new BuildSequencePlan(
			"layer-1", "stage-1", 8.0, 16.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false, null, 1
		);
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 8, SectionType.VERSE, "verse"),
				new ChoreographyPlan.SectionPlan(8, 16, SectionType.BUILD, "build")
			),
			List.of(),
			List.of(),
			List.of(
				new ChoreographyPlan.CameraPhrase(2.0, "HOLD", 0),
				new ChoreographyPlan.CameraPhrase(10.0, "SHAKE", 1),
				new ChoreographyPlan.CameraPhrase(18.0, "PAN", 1)
			),
			List.of(),
			DensityCurve.uniform(0.5),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(),
			List.of(),
			List.of(sequence)
		);

		ChoreographyPlan shaped = BuildRevealCameraPlanner.apply(plan);

		assertTrue(shaped.cameraPhrases().stream().anyMatch(c -> c.timeSeconds() == 2.0));
		assertFalse(shaped.cameraPhrases().stream().anyMatch(c ->
			"SHAKE".equalsIgnoreCase(c.movement()) || "SHAKE".equalsIgnoreCase(c.action())));
		assertTrue(shaped.cameraPhrases().stream().anyMatch(c ->
			c.movement().contains("ORBIT") || c.action().contains("ORBIT")));
		assertTrue(shaped.cameraPhrases().stream().anyMatch(c ->
			c.movement().contains("PUSH_IN") || c.action().contains("PUSH") || c.action().contains("ZOOM")));
		// outside window kept (18.0 may be outside 8-16)
		assertTrue(shaped.cameraPhrases().stream().anyMatch(c -> Math.abs(c.timeSeconds() - 18.0) < 1e-6
			|| c.timeSeconds() > 16.0));
	}

	@Test
	void applyNoopWithoutBuildSequences() {
		ChoreographyPlan plan = new ChoreographyPlan(
			List.of(new ChoreographyPlan.SectionPlan(0, 8, SectionType.VERSE, "verse")),
			List.of(),
			List.of(),
			List.of(new ChoreographyPlan.CameraPhrase(1.0, "HOLD", 0)),
			List.of(),
			DensityCurve.uniform(0.5)
		);

		assertEquals(plan, BuildRevealCameraPlanner.apply(plan));
	}

	@Test
	void shortWindowCollapsesToTwoShots() {
		BuildSequencePlan sequence = new BuildSequencePlan(
			"layer-1", "stage-1", 0.0, 1.0,
			BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false, null, 0
		);

		List<CameraShot> shots = BuildRevealCameraPlanner.planShots(sequence);
		assertEquals(2, shots.size());
		assertEquals(CameraShotMovement.HOLD, shots.get(0).movement());
		assertEquals(CameraShotMovement.PUSH_IN, shots.get(1).movement());
	}
}
