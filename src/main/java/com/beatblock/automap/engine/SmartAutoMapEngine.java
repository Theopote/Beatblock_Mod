package com.beatblock.automap.engine;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapConfigFactory;
import com.beatblock.automap.camera.CameraContinuityPlanner;
import com.beatblock.automap.camera.CameraPlanningContext;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.choreography.BuildRevealCameraPlanner;
import com.beatblock.automap.choreography.BuildRevealPlanner;
import com.beatblock.automap.choreography.BuildSequencePlan;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanBuilder;
import com.beatblock.automap.choreography.ChoreographyCompileOptions;
import com.beatblock.automap.choreography.ChoreographyPlanCompiler;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.ChoreographyStructureMerger;
import com.beatblock.automap.performance.PerformancePlanShaper;
import com.beatblock.automap.performance.PerformanceProfile;
import com.beatblock.automap.vfx.VfxPlanner;
import com.beatblock.timeline.generation.ContentReplacePolicy;
import com.beatblock.timeline.generation.TimelineGeneratorIds;
import com.beatblock.timeline.Timeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Smart Auto-Map Engine 门面：音乐 → 编舞计划 → Timeline（方块动画、摄像机、粒子）。
 * <p>
 * 流程：AudioFeatureTimeline → 音乐结构 → {@link ChoreographyPlan} → Timeline Draft。
 */
public final class SmartAutoMapEngine {

	private static final Logger LOGGER = LoggerFactory.getLogger(SmartAutoMapEngine.class);

	/**
	 * 根据音频特征与设置生成完整编排并写入 Timeline。
	 *
	 * @param featureTimeline 来自 Audio Analysis Engine 的分析结果（可为 null，则仅清空不生成）
	 * @param settings        风格、复杂度、镜头/粒子开关
	 * @param timeline        目标时间线
	 * @return 生成统计
	 */
	public static AutoMapResult generate(AudioFeatureTimeline featureTimeline, AutoMapSettings settings, Timeline timeline) {
		AutoMapResult result = new AutoMapResult(0, 0, 0, 0, "");
		if (timeline == null) return result;
		if (featureTimeline == null || settings == null) {
			ContentReplacePolicy replaceSmartAutomap = ContentReplacePolicy.replaceGenerator(TimelineGeneratorIds.SMART_AUTOMAP);
			timeline.applyContentReplacePolicy(Timeline.TRACK_ID_ANIMATION_AUTO, replaceSmartAutomap);
			timeline.applyContentReplacePolicy(Timeline.TRACK_ID_CAMERA, replaceSmartAutomap);
			timeline.applyContentReplacePolicy(Timeline.TRACK_ID_GLOBAL, replaceSmartAutomap);
			LOGGER.info("BeatBlock Smart Auto-Map: 无音频分析结果或设置，已清空 smart-automap 自动轨道");
			return result;
		}
		double duration = featureTimeline.getDurationSeconds();
		float bpm = featureTimeline.getBpm();
		List<FrequencyBands> bands = featureTimeline.getBands();
		List<DetectedBeat> beats = featureTimeline.getBeats();

		MusicStructure musicStructure = MusicStructureAnalyzer.analyze(featureTimeline);
		List<StructuralSection> sections = musicStructure.sections();
		List<RhythmEvent> rhythmEvents = RhythmClassifier.classify(beats, bands);
		rhythmEvents = PatternGenerator.filter(rhythmEvents, settings, musicStructure);

		PerformanceProfile performanceProfile = settings.getPerformanceProfile();
		com.beatblock.automap.cast.StageCast stageCast =
			com.beatblock.automap.cast.StageCast.fromTargetIds(settings.getTargetObjectIds());
		CameraPlanningContext cameraContext = new CameraPlanningContext(
			bpm, duration, settings.getStyle(), settings.getTargetObjectIds(), stageCast, List.of());
		List<CameraShot> cameraShots = settings.isCameraEnabled()
			? CameraContinuityPlanner.plan(CameraDirector.generateShots(sections, cameraContext, true))
			: List.of();
		if (performanceProfile != null) {
			cameraShots = PerformancePlanShaper.filterShots(cameraShots, performanceProfile.camera());
		}
		List<ParticleEvent> particleEvents = List.of();

		AutoMapConfig config = AutoMapConfigFactory.fromSettings(settings);
		ChoreographyPlan analyzed = ChoreographyPlanBuilder.fromMusicStructure(
			musicStructure,
			rhythmEvents,
			cameraShots,
			particleEvents,
			settings.getStyle(),
			config
		);
		analyzed = settings.getLayerProfile().apply(analyzed);
		analyzed = VfxPlanner.apply(analyzed, bands, settings);
		analyzed = PerformancePlanShaper.apply(analyzed, performanceProfile);
		analyzed = applyBuildRevealSemantics(analyzed, settings);
		analyzed = BuildRevealCameraPlanner.apply(analyzed);
		ChoreographyPlan existing = ChoreographyPlanStore.loadPlan(timeline);
		ChoreographyPlan plan = ChoreographyStructureMerger.merge(existing, analyzed);

		ChoreographyPlanCompiler.SmartAutoMapCompileResult compiled =
			ChoreographyPlanCompiler.compileAll(
				timeline, plan, ChoreographyCompileOptions.smartAutoMap());

		ChoreographyPlanStore.save(timeline, plan, config);

		result = new AutoMapResult(
			compiled.animationEvents(),
			compiled.cameraEvents(),
			compiled.vfxEvents(),
			sections.size(),
			compiled.generationId(),
			compiled.buildEvents()
		);
		LOGGER.info(
			"BeatBlock Smart Auto-Map: 动画 {} 个, 镜头 {} 个, 粒子 {} 个, BUILD {} 个, 段落 {} 个, 小节 {} 个, 乐句 {} 个",
			compiled.animationEvents(), compiled.cameraEvents(), compiled.vfxEvents(), compiled.buildEvents(),
			sections.size(),
			plan.musicalStructure().bars().size(), plan.musicalStructure().phrases().size());
		return result;
	}

	/**
	 * BUILD_REVEAL：附加 {@link BuildSequencePlan}，并剥离会冒充 reveal 的动画 Phrase。
	 * 镜头由后续 {@link BuildRevealCameraPlanner} 按建造进度重写。
	 */
	public static ChoreographyPlan applyBuildRevealSemantics(ChoreographyPlan plan, AutoMapSettings settings) {
		if (plan == null || settings == null || !settings.hasBuildLayerId()) {
			return plan != null ? plan : ChoreographyPlan.empty();
		}
		String targetObjectId = settings.getTargetObjectIds().isEmpty()
			? ""
			: settings.getTargetObjectIds().getFirst();
		BuildSequencePlan sequence = BuildRevealPlanner.plan(plan, settings.getBuildLayerId(), targetObjectId);
		if (sequence == null) {
			return plan;
		}
		return new ChoreographyPlan(
			plan.sections(),
			plan.stageRoles(),
			List.of(),
			plan.cameraPhrases(),
			List.of(),
			plan.densityCurve(),
			plan.sectionEdits(),
			plan.musicalStructure(),
			List.of(),
			List.of(),
			List.of(sequence)
		);
	}

	/** 生成结果统计 */
	public static final class AutoMapResult {
		private final int animationEvents;
		private final int cameraEvents;
		private final int particleEvents;
		private final int sections;
		private final String generationId;
		private final int buildEvents;

		public AutoMapResult(int animationEvents, int cameraEvents, int particleEvents, int sections) {
			this(animationEvents, cameraEvents, particleEvents, sections, "", 0);
		}

		public AutoMapResult(
			int animationEvents,
			int cameraEvents,
			int particleEvents,
			int sections,
			String generationId
		) {
			this(animationEvents, cameraEvents, particleEvents, sections, generationId, 0);
		}

		public AutoMapResult(
			int animationEvents,
			int cameraEvents,
			int particleEvents,
			int sections,
			String generationId,
			int buildEvents
		) {
			this.animationEvents = animationEvents;
			this.cameraEvents = cameraEvents;
			this.particleEvents = particleEvents;
			this.sections = sections;
			this.generationId = generationId != null ? generationId : "";
			this.buildEvents = Math.max(0, buildEvents);
		}

		public int getAnimationEvents() { return animationEvents; }
		public int getCameraEvents() { return cameraEvents; }
		public int getParticleEvents() { return particleEvents; }
		public int getSections() { return sections; }
		public String getGenerationId() { return generationId; }
		public int getBuildEvents() { return buildEvents; }
	}
}
