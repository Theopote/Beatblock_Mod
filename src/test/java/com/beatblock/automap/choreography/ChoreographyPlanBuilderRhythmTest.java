package com.beatblock.automap.choreography;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.camera.CameraShotCodec;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.CameraAction;
import com.beatblock.automap.engine.CameraEvent;
import com.beatblock.automap.engine.ParticleEvent;
import com.beatblock.automap.engine.ParticleType;
import com.beatblock.automap.engine.RhythmEvent;
import com.beatblock.automap.engine.RhythmType;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyPlanBuilderRhythmTest {

	@Test
	void buildsMotionCameraAndVfxPhrasesFromRhythmAnalysis() {
		List<RhythmEvent> rhythms = List.of(
			new RhythmEvent(1.0, RhythmType.KICK, 0.8f),
			new RhythmEvent(1.1, RhythmType.SNARE, 0.7f)
		);
		List<StructuralSection> sections = List.of(
			new StructuralSection(0, 16, SectionType.VERSE)
		);
		List<com.beatblock.automap.camera.CameraShot> cameras = List.of(
			CameraShotCodec.legacyShot(4.0, CameraAction.PAN, -1)
		);
		List<ParticleEvent> particles = List.of(new ParticleEvent(2.0, ParticleType.SPARK));

		AutoMapConfig config = AutoMapConfig.builder()
			.targetForFeature("low", "stage-kick")
			.targetForFeature("mid", "stage-snare")
			.build();

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromRhythmAnalysis(
			rhythms, sections, cameras, particles, AutoMapStyle.EDM, config);

		assertEquals(2, plan.motionPhrases().size());
		assertEquals("low", plan.motionPhrases().get(0).normalizedFeatureKey());
		assertEquals("mid", plan.motionPhrases().get(1).normalizedFeatureKey());
		assertEquals(2, plan.stageRoles().size());
		assertEquals(1, plan.cameraPhrases().size());
		assertTrue(plan.cameraPhrases().getFirst().action().contains("PAN"));
		assertEquals(1, plan.vfxPhrases().size());
		assertEquals("particle_spark", plan.vfxPhrases().getFirst().vfxKind());
		assertTrue(plan.densityCurve().sampleAt(0) > 0);
	}
}
