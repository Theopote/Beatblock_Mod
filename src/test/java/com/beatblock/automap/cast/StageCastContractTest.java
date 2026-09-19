package com.beatblock.automap.cast;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapConfigFactory;
import com.beatblock.automap.camera.CameraPlanningContext;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraSubjectKind;
import com.beatblock.automap.camera.CameraSubjectPlanner;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanBuilder;
import com.beatblock.automap.choreography.ChoreographyVfx;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.CameraDirector;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;
import com.beatblock.automap.vfx.VfxPlanner;
import com.beatblock.creator.CreationPreset;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageCastContractTest {

	@Test
	void fromTargetIdsAssignsRolesBeyondThreeObjects() {
		StageCast cast = StageCast.fromTargetIds(List.of(
			"tower", "wing-l", "wing-r", "roof", "sign"
		));

		assertEquals(StageRole.HERO, cast.roleOf("tower"));
		assertEquals(StageRole.LEAD, cast.roleOf("wing-l"));
		assertEquals(StageRole.SUPPORT, cast.roleOf("wing-r"));
		assertEquals(StageRole.ACCENT, cast.roleOf("roof"));
		assertEquals(StageRole.BACKGROUND, cast.roleOf("sign"));
		assertEquals(5, cast.allObjectIds().size());
		assertEquals(4, cast.choreographyParticipantIds().size());
		assertFalse(cast.choreographyParticipantIds().contains("sign"));
	}

	@Test
	void autoMapConfigMapsAllCastMembersAsStageRoles() {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setTargetObjectIds(List.of("a", "b", "c", "d", "e"));
		AutoMapConfig config = AutoMapConfigFactory.fromSettings(settings);

		assertEquals("a", config.getTargetByNormalizedFeature().get("low"));
		assertEquals("b", config.getTargetByNormalizedFeature().get("mid"));
		assertEquals("c", config.getTargetByNormalizedFeature().get("high"));
		assertTrue(config.getTargetByNormalizedFeature().containsKey("cast:d"));
		assertTrue(config.getTargetByNormalizedFeature().containsKey("cast:e"));
	}

	@Test
	void cameraSubjectPlannerUsesHeroOnDropNotRoundRobin() {
		StageCast cast = StageCast.fromTargetIds(List.of("tower", "wing-l", "wing-r", "roof", "sign"));
		StructuralSection drop = new StructuralSection(16, 24, SectionType.DROP);
		CameraPlanningContext ctx = new CameraPlanningContext(
			128f, 64, AutoMapStyle.EDM, cast.allObjectIds(), cast, List.of());

		CameraSubjectPlanner.Resolution r0 = CameraSubjectPlanner.resolve(drop, 0, cast, List.of(), null, ctx);
		CameraSubjectPlanner.Resolution r3 = CameraSubjectPlanner.resolve(drop, 3, cast, List.of(), null, ctx);

		assertEquals(CameraSubjectKind.STAGE_OBJECT, r0.primary().kind());
		assertEquals("tower", r0.focusedObjectId());
		assertEquals("tower", r3.focusedObjectId());
	}

	@Test
	void fiveObjectPlanKeepsAllFocusableInGrammarAndHeroVfxOnHero() {
		List<String> ids = List.of("tower", "wing-l", "wing-r", "roof", "sign");
		AutoMapSettings settings = CreationPreset.FULL_CHOREOGRAPHY.buildAutoMapSettings("tower");
		settings.setTargetObjectIds(ids);

		List<StructuralSection> sections = List.of(
			new StructuralSection(0, 8, SectionType.INTRO),
			new StructuralSection(8, 16, SectionType.VERSE),
			new StructuralSection(16, 24, SectionType.BUILD),
			new StructuralSection(24, 32, SectionType.DROP),
			new StructuralSection(32, 40, SectionType.CHORUS),
			new StructuralSection(40, 48, SectionType.BREAK),
			new StructuralSection(48, 56, SectionType.CHORUS),
			new StructuralSection(56, 64, SectionType.OUTRO)
		);
		StageCast cast = StageCast.fromTargetIds(ids);
		CameraPlanningContext cameraContext = new CameraPlanningContext(
			120f, 64, AutoMapStyle.EDM, ids, cast, List.of());
		List<CameraShot> shots = CameraDirector.generateShots(sections, cameraContext, true);

		AutoMapConfig config = AutoMapConfigFactory.fromSettings(settings);
		ChoreographyPlan plan = ChoreographyPlanBuilder.fromMusicStructure(
			new com.beatblock.audio.analysis.structure.MusicStructure(
				64, List.of(), List.of(), List.of(), sections),
			List.of(),
			shots,
			List.of(),
			AutoMapStyle.EDM,
			config
		);
		plan = VfxPlanner.apply(plan, List.of(), settings);

		Set<String> roleIds = new HashSet<>(plan.stageRoles().stream()
			.map(ChoreographyPlan.StageRoleAssignment::targetObjectId)
			.toList());
		assertTrue(roleIds.containsAll(ids), "all five objects should be in stage roles: " + roleIds);

		assertTrue(plan.choreographyPhrases().stream().anyMatch(p -> p.isHero()));
		assertTrue(plan.choreographyPhrases().stream()
			.filter(p -> !p.isHero())
			.anyMatch(p -> p.targets().size() >= 4));

		List<ChoreographyVfx.ParticleBurst> heroBursts = new ArrayList<>();
		for (ChoreographyVfx vfx : plan.vfxPhrases()) {
			if (vfx instanceof ChoreographyVfx.ParticleBurst burst && "hero_burst".equals(burst.name())) {
				heroBursts.add(burst);
			}
		}
		assertFalse(heroBursts.isEmpty());
		assertTrue(heroBursts.stream().allMatch(b ->
			b.target().kind() == CameraSubjectKind.STAGE_OBJECT
				&& "tower".equals(b.target().refId())));

		// Drop cameras should focus hero, not sectionIndex%N == sign
		assertTrue(shots.stream()
			.filter(s -> s.sectionIndex() == 3)
			.anyMatch(s -> s.subject().kind() == CameraSubjectKind.STAGE_OBJECT
				&& "tower".equals(s.subject().refId())));
	}
}
