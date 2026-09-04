package com.beatblock.automap.choreography;

import com.beatblock.automap.AutoMapGenerator;
import com.beatblock.automap.choreography.grammar.ChoreographyConflictResolver;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.ChoreographyPhraseInstance;
import com.beatblock.automap.choreography.grammar.ChoreographyPhraseInstanceMaterializer;
import com.beatblock.automap.choreography.grammar.PhraseGrammarExpander;
import com.beatblock.automap.choreography.grammar.PhraseTriggerContextFactory;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.automap.camera.CameraContinuityPlanner;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotCodec;
import com.beatblock.automap.camera.CameraShotTimelineWriter;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.generation.ContentReplacePolicy;
import com.beatblock.timeline.generation.GenerationSession;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.timeline.playback.GlobalEventPayload;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link ChoreographyPlan} 编译为 Timeline 草稿事件。
 * <p>
 * 动画编译顺序与语义层：
 * <ol>
 *   <li>{@link ChoreographyLayer#ACCENT} — {@link ChoreographyPlan.MotionPhrase}</li>
 *   <li>{@link ChoreographyLayer#PHRASE} — {@link SpatialMotifPhrase}（legacy）与常规 Grammar Phrase</li>
 *   <li>{@link ChoreographyLayer#HERO} — Grammar Phrase with {@link com.beatblock.automap.choreography.grammar.ChoreographyPhrase#isHero()}</li>
 * </ol>
 * Accent / Phrase / Hero 可并存，但默认强度权重不同。
 * 流水线：Trigger → {@link com.beatblock.automap.choreography.grammar.ChoreographyPhraseInstance}
 * → {@link ChoreographyBudgetEnforcer#enforceInstances}
 * → {@link com.beatblock.automap.choreography.grammar.ChoreographyConflictResolver}
 * → Expand → Timeline。
 * 空间 Phrase / Hero 以 Instance 为单位裁剪，不会拆烂放射 / 镜像等完整图形。
 * <p>
 * 编译只读取 Plan（含 {@link ChoreographyPlan#stageRoles()}），不依赖 {@link com.beatblock.automap.AutoMapConfig}。
 */
public final class ChoreographyPlanCompiler {

	private ChoreographyPlanCompiler() {}

	public static int compileAnimationEvents(
		Timeline timeline,
		ChoreographyPlan plan,
		ChoreographyCompileOptions options
	) {
		if (timeline == null || plan == null || options == null) return 0;
		GenerationSession session = options.resolveSession(timeline);
		return compileAnimationEvents(
			timeline,
			plan,
			options.animationMode(),
			options.minGapSeconds(),
			session
		);
	}

	public static int compileAnimationEvents(
		Timeline timeline,
		ChoreographyPlan plan,
		ReplaceMode mode
	) {
		return compileAnimationEvents(timeline, plan, ChoreographyCompileOptions.animationOnly(mode));
	}

	public static int compileAnimationEvents(
		Timeline timeline,
		ChoreographyPlan plan,
		ReplaceMode mode,
		double minGapSeconds
	) {
		if (timeline == null || plan == null) return 0;
		ReplaceMode replaceMode = mode != null ? mode : ReplaceMode.APPEND;
		GenerationSession session = GenerationSession.create(
			com.beatblock.timeline.generation.TimelineGeneratorIds.SMART_AUTOMAP,
			timeline
		);
		return compileAnimationEvents(
			timeline,
			plan,
			replaceMode.toPolicy(),
			minGapSeconds,
			session
		);
	}

	private static int compileAnimationEvents(
		Timeline timeline,
		ChoreographyPlan plan,
		ContentReplacePolicy policy,
		double minGapSeconds,
		GenerationSession session
	) {
		ChoreographyCompileApplicator.applyAnimation(timeline, policy);

		String fallbackTarget = AutoMapGenerator.resolveTargetObjectId();
		List<ChoreographyPlan.MotionPhrase> candidates = new ArrayList<>();
		for (ChoreographyPlan.MotionPhrase phrase : plan.motionPhrases()) {
			if (!ChoreographyPlanEditor.isMotionEnabled(plan, phrase)) continue;

			double density = plan.densityCurve().sampleAt(phrase.timeSeconds());
			double densityThreshold = ChoreographyPlanEditor.resolveDensityThreshold(plan, phrase, 0.15);
			if (density < densityThreshold) continue;

			candidates.add(phrase);
		}

		List<ChoreographyPlan.MotionPhrase> resolved = ChoreographyMotionGapResolver.resolve(
			candidates,
			minGapSeconds > 0 ? minGapSeconds : ChoreographyCompileOptions.DEFAULT_MIN_GAP_SECONDS
		);
		TimingSnapResolver.SnapContext snapContext = TimingSnapResolver.SnapContext.from(plan);

		List<ChoreographyPhraseInstance> instances = new ArrayList<>();
		int accentOrdinal = 0;
		for (ChoreographyPlan.MotionPhrase phrase : resolved) {
			String targetId = plan.resolveTargetObjectId(phrase.normalizedFeatureKey(), fallbackTarget);
			instances.add(ChoreographyPhraseInstanceMaterializer.fromAccent(
				phrase, targetId, accentOrdinal++, plan, snapContext
			));
		}
		instances.addAll(materializeSpatialMotifInstances(plan, snapContext));
		instances.addAll(materializeGrammarInstances(timeline, plan, snapContext));
		instances = ChoreographyBudgetEnforcer.enforceInstances(instances, plan);
		instances = ChoreographyConflictResolver.resolve(instances);

		List<TimelineAnimationEvent> draft = expandInstances(instances, plan, session);
		return TimelineDraftWriter.writeAutoGeneratedEvents(timeline, draft, false);
	}


	private static List<ChoreographyPhraseInstance> materializeSpatialMotifInstances(
		ChoreographyPlan plan,
		TimingSnapResolver.SnapContext snapContext
	) {
		if (plan.spatialMotifPhrases().isEmpty()) return List.of();
		List<ChoreographyPhraseInstance> out = new ArrayList<>();
		int spatialOrdinal = 0;
		for (SpatialMotifPhrase phrase : plan.spatialMotifPhrases()) {
			if (!ChoreographyPlanEditor.isSpatialMotifEnabled(plan, phrase)) {
				spatialOrdinal++;
				continue;
			}
			double density = plan.densityCurve().sampleAt(phrase.timeSeconds());
			if (density < 0.15) {
				spatialOrdinal++;
				continue;
			}
			out.add(ChoreographyPhraseInstanceMaterializer.fromSpatialMotif(
				phrase, spatialOrdinal, plan, snapContext
			));
			spatialOrdinal++;
		}
		return out;
	}

	private static List<ChoreographyPhraseInstance> materializeGrammarInstances(
		Timeline timeline,
		ChoreographyPlan plan,
		TimingSnapResolver.SnapContext snapContext
	) {
		if (timeline == null || plan == null || plan.choreographyPhrases().isEmpty()) {
			return List.of();
		}
		var globalContext = PhraseTriggerContextFactory.fromTimeline(
			timeline,
			plan.musicalStructure().beatTimes()
		);
		List<ChoreographyPhraseInstance> out = new ArrayList<>();
		int grammarOrdinal = 0;
		for (ChoreographyPhrase phrase : plan.choreographyPhrases()) {
			if (!ChoreographyPlanEditor.isGrammarPhraseEnabled(plan, phrase)) {
				grammarOrdinal++;
				continue;
			}
			var phraseContext = resolveGrammarPhraseTriggerContext(globalContext, plan, phrase);
			out.addAll(ChoreographyPhraseInstanceMaterializer.fromGrammarPhrase(
				phrase, phraseContext, grammarOrdinal, plan, snapContext
			));
			grammarOrdinal++;
		}
		return out;
	}

	private static List<TimelineAnimationEvent> expandInstances(
		List<ChoreographyPhraseInstance> instances,
		ChoreographyPlan plan,
		GenerationSession session
	) {
		if (instances.isEmpty()) return List.of();
		BlockAnimationEngine animationEngine = SpatialMotifLayoutResolver.currentAnimationEngine();
		List<TimelineAnimationEvent> draft = new ArrayList<>();
		for (ChoreographyPhraseInstance instance : instances) {
			SpatialMotifLayout layout = SpatialMotifLayoutResolver.resolve(
				instance.targetIds(),
				instance.spatial().resolvedAxis(),
				animationEngine
			);
			List<PhraseGrammarExpander.ExpandedPhraseEvent> expanded =
				PhraseGrammarExpander.expand(instance, layout);
			int targetCount = Math.max(1, expanded.size());
			for (PhraseGrammarExpander.ExpandedPhraseEvent event : expanded) {
				TimelineGenerationMetadata metadata = session.forPhrase(
					instance.sectionIndex(),
					instance.phraseIndex() >= 0
						? instance.phraseIndex()
						: plan.musicalPhraseIndexAt(event.timeSeconds())
				);
				Map<String, Object> params = ChoreographyPhraseBatchSupport.tagAtomicBatch(
					event.params(),
					instance.instanceId(),
					instance.sourcePhraseId(),
					targetCount
				);
				TimelineAnimationEvent timelineEvent = new TimelineAnimationEvent(
					"",
					event.timeSeconds(),
					event.durationSeconds(),
					event.primitiveId(),
					event.targetObjectId(),
					event.energy(),
					params
				);
				draft.add(withLayerMetadata(timelineEvent, metadata, instance.layer()));
			}
		}
		return draft;
	}

	private static TimelineAnimationEvent withLayerMetadata(
		TimelineAnimationEvent event,
		TimelineGenerationMetadata metadata,
		ChoreographyLayer layer
	) {
		Map<String, Object> params = layer.scaleEventParams(event.getParameters(), event.getEnergy());
		TimelineAnimationEvent scaled = new TimelineAnimationEvent(
			event.getEventId(),
			event.getTimeSeconds(),
			event.getDurationSeconds(),
			event.getAnimationTypeId(),
			event.getTargetObjectId(),
			layer.scaleEnergy(event.getEnergy()),
			params
		);
		return TimelineDraftWriter.withMetadata(scaled, metadata);
	}

	private static com.beatblock.automap.choreography.grammar.PhraseTriggerContext resolveGrammarPhraseTriggerContext(
		com.beatblock.automap.choreography.grammar.PhraseTriggerContext globalContext,
		ChoreographyPlan plan,
		com.beatblock.automap.choreography.grammar.ChoreographyPhrase phrase
	) {
		int sectionIndex = phrase.sectionIndex();
		if (sectionIndex < 0 || sectionIndex >= plan.sections().size()) {
			return globalContext;
		}
		return com.beatblock.automap.choreography.grammar.PhraseTriggerContext.forSection(
			globalContext,
			plan.sections().get(sectionIndex)
		);
	}

	public static int compileCameraEvents(Timeline timeline, ChoreographyPlan plan) {
		return compileCameraEvents(timeline, plan, ReplaceMode.REPLACE_GENERATED);
	}

	public static int compileCameraEvents(Timeline timeline, ChoreographyPlan plan, ReplaceMode mode) {
		return compileCameraEvents(timeline, plan, mode != null ? mode.toPolicy() : ContentReplacePolicy.append(), null);
	}

	public static int compileCameraEvents(
		Timeline timeline,
		ChoreographyPlan plan,
		ContentReplacePolicy policy
	) {
		return compileCameraEvents(timeline, plan, policy, null);
	}

	private static int compileCameraEvents(
		Timeline timeline,
		ChoreographyPlan plan,
		ContentReplacePolicy policy,
		@Nullable GenerationSession session
	) {
		if (timeline == null || plan == null) return 0;
		ChoreographyCompileApplicator.applyCamera(timeline, policy);
		if (plan.cameraPhrases().isEmpty()) return 0;
		GenerationSession resolved = session != null
			? session
			: GenerationSession.create(
				com.beatblock.timeline.generation.TimelineGeneratorIds.SMART_AUTOMAP,
				timeline
			);
		TimingSnapResolver.SnapContext snapContext = TimingSnapResolver.SnapContext.from(plan);
		List<CameraShot> decoded = new ArrayList<>();
		List<TimelineGenerationMetadata> metadataByShot = new ArrayList<>();
		for (ChoreographyPlan.CameraPhrase phrase : plan.cameraPhrases()) {
			if (!ChoreographyPlanEditor.isCameraEnabled(plan, phrase)) continue;
			double eventTime = TimingSnapResolver.snap(
				phrase.timeSeconds(),
				phrase.timingSnap(),
				snapContext
			);
			TimelineGenerationMetadata metadata = resolved.forPhrase(
				phrase.sectionIndex(),
				plan.musicalPhraseIndexAt(eventTime)
			);
			decoded.add(CameraShotCodec.fromPhrase(phrase.withTimeSeconds(eventTime)));
			metadataByShot.add(metadata);
		}
		List<CameraShot> planned = CameraContinuityPlanner.plan(decoded);
		List<CameraShotTimelineWriter.TaggedShot> shots = new ArrayList<>(planned.size());
		for (int i = 0; i < planned.size(); i++) {
			shots.add(new CameraShotTimelineWriter.TaggedShot(planned.get(i), metadataByShot.get(i)));
		}
		return CameraShotTimelineWriter.writeTagged(timeline, shots);
	}

	public static int compileVfxEvents(Timeline timeline, ChoreographyPlan plan) {
		return compileVfxEvents(timeline, plan, ReplaceMode.REPLACE_GENERATED);
	}

	public static int compileVfxEvents(Timeline timeline, ChoreographyPlan plan, ReplaceMode mode) {
		return compileVfxEvents(timeline, plan, mode != null ? mode.toPolicy() : ContentReplacePolicy.append(), null);
	}

	public static int compileVfxEvents(
		Timeline timeline,
		ChoreographyPlan plan,
		ContentReplacePolicy policy
	) {
		return compileVfxEvents(timeline, plan, policy, null);
	}

	private static int compileVfxEvents(
		Timeline timeline,
		ChoreographyPlan plan,
		ContentReplacePolicy policy,
		@Nullable GenerationSession session
	) {
		if (timeline == null || plan == null) return 0;
		ChoreographyCompileApplicator.applyVfx(timeline, policy);
		if (plan.vfxPhrases().isEmpty()) return 0;
		GenerationSession resolved = session != null
			? session
			: GenerationSession.create(
				com.beatblock.timeline.generation.TimelineGeneratorIds.SMART_AUTOMAP,
				timeline
			);
		TimingSnapResolver.SnapContext snapContext = TimingSnapResolver.SnapContext.from(plan);
		int count = 0;
		for (ChoreographyVfx phrase : plan.vfxPhrases()) {
			if (!ChoreographyPlanEditor.isVfxEnabled(plan, phrase)) continue;
			double eventTime = TimingSnapResolver.snap(
				phrase.timeSeconds(),
				TimingSnapDefaults.forVfx(phrase),
				snapContext
			);
			GlobalEventPayload payload = ChoreographyVfxPayloadMapper.toPayload(phrase);
			TimelineGenerationMetadata metadata = resolved.forPhrase(
				phrase.sectionIndex(),
				plan.musicalPhraseIndexAt(eventTime)
			);
			timeline.addGlobalPayloadEvent(eventTime, payload, metadata);
			count++;
		}
		return count;
	}

	/** 编译动画、镜头与 VFX 短语（Smart Auto-Map 统一入口）。 */
	public static SmartAutoMapCompileResult compileAll(
		Timeline timeline,
		ChoreographyPlan plan,
		ChoreographyCompileOptions options
	) {
		ChoreographyCompileOptions compileOptions = options != null ? options : ChoreographyCompileOptions.smartAutoMap();
		GenerationSession session = compileOptions.resolveSession(timeline);
		int animations = compileAnimationEvents(
			timeline,
			plan,
			compileOptions.animationMode(),
			compileOptions.minGapSeconds(),
			session
		);
		int cameras = compileCameraEvents(timeline, plan, compileOptions.cameraMode(), session);
		int vfx = compileVfxEvents(timeline, plan, compileOptions.vfxMode(), session);
		return new SmartAutoMapCompileResult(animations, cameras, vfx);
	}

	/**
	 * 仅重编译指定段落：清除该段 smart-automap 归属内容，并只写入该段短语。
	 */
	public static SmartAutoMapCompileResult compileSection(
		Timeline timeline,
		ChoreographyPlan plan,
		int sectionIndex
	) {
		if (timeline == null || plan == null || sectionIndex < 0) {
			return new SmartAutoMapCompileResult(0, 0, 0);
		}
		return compileAll(
			timeline,
			filterPlanToSection(plan, sectionIndex),
			ChoreographyCompileOptions.forSection(sectionIndex)
		);
	}

	private static ChoreographyPlan filterPlanToSection(ChoreographyPlan plan, int sectionIndex) {
		return new ChoreographyPlan(
			plan.sections(),
			plan.stageRoles(),
			ChoreographyPlanEditor.motionPhrasesInSection(plan, sectionIndex),
			ChoreographyPlanEditor.cameraPhrasesInSection(plan, sectionIndex),
			ChoreographyPlanEditor.vfxPhrasesInSection(plan, sectionIndex),
			plan.densityCurve(),
			plan.sectionEdits(),
			plan.musicalStructure(),
			ChoreographyPlanEditor.spatialMotifPhrasesInSection(plan, sectionIndex),
			ChoreographyPlanEditor.choreographyPhrasesInSection(plan, sectionIndex)
		);
	}

	public record SmartAutoMapCompileResult(int animationEvents, int cameraEvents, int vfxEvents) {}
}
