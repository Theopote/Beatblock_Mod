package com.beatblock.automap.golden;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapConfigFactory;
import com.beatblock.automap.camera.CameraContinuityPlanner;
import com.beatblock.automap.camera.CameraPlanningContext;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.choreography.BuildRevealCameraPlanner;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanBuilder;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.CameraDirector;
import com.beatblock.automap.engine.ParticleEvent;
import com.beatblock.automap.engine.PatternGenerator;
import com.beatblock.automap.engine.RhythmClassifier;
import com.beatblock.automap.engine.RhythmEvent;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.automap.performance.PerformancePlanShaper;
import com.beatblock.automap.performance.PerformanceProfile;
import com.beatblock.automap.vfx.VfxPlanner;
import com.beatblock.creator.CreationPreset;

import java.util.List;

/**
 * 与 {@link SmartAutoMapEngine} 同序的计划生成 harness（注入 {@link SongStyleFixture} 结构）。
 */
public final class SongStyleGenerationHarness {

	private SongStyleGenerationHarness() {}

	public static GenerationFingerprint generate(SongStyleFixture style, CreationPreset preset) {
		SongStyleFixture.Bundle bundle = style.build();
		String layerId = preset.wantsBuildLayer() ? "golden-layer" : null;
		AutoMapSettings settings = preset.buildAutoMapSettings("tower-a", layerId);
		if (settings == null) {
			throw new IllegalArgumentException("Preset does not produce AutoMapSettings: " + preset);
		}
		// Phrase/Hero grammar needs ≥2 stage participants
		settings.setTargetObjectIds(List.of("tower-a", "tower-b", "tower-c"));
		ChoreographyPlan plan = generatePlan(bundle, settings);
		return GenerationFingerprint.from(style, preset.name(), plan);
	}

	public static ChoreographyPlan generatePlan(SongStyleFixture.Bundle bundle, AutoMapSettings settings) {
		var timeline = bundle.timeline();
		var structure = bundle.structure();
		PerformanceProfile profile = settings.getPerformanceProfile();

		List<RhythmEvent> rhythmEvents = RhythmClassifier.classify(timeline.getBeats(), timeline.getBands());
		rhythmEvents = PatternGenerator.filter(rhythmEvents, settings, structure);

		CameraPlanningContext cameraContext = new CameraPlanningContext(
			timeline.getBpm(),
			timeline.getDurationSeconds(),
			settings.getStyle(),
			settings.getTargetObjectIds(),
			com.beatblock.automap.cast.StageCast.fromTargetIds(settings.getTargetObjectIds()),
			List.of()
		);
		List<CameraShot> cameraShots = settings.isCameraEnabled()
			? CameraContinuityPlanner.plan(CameraDirector.generateShots(structure.sections(), cameraContext, true))
			: List.of();
		if (profile != null) {
			cameraShots = PerformancePlanShaper.filterShots(cameraShots, profile.camera());
		}

		AutoMapConfig config = AutoMapConfigFactory.fromSettings(settings);
		ChoreographyPlan plan = ChoreographyPlanBuilder.fromMusicStructure(
			structure,
			rhythmEvents,
			cameraShots,
			List.<ParticleEvent>of(),
			settings.getStyle(),
			config
		);
		plan = settings.getLayerProfile().apply(plan);
		plan = VfxPlanner.apply(plan, timeline.getBands(), settings);
		plan = PerformancePlanShaper.apply(plan, profile);
		plan = SmartAutoMapEngine.applyBuildRevealSemantics(plan, settings);
		plan = BuildRevealCameraPlanner.apply(plan);
		return plan;
	}
}
