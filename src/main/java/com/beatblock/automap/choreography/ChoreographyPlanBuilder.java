package com.beatblock.automap.choreography;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapGenerator;
import com.beatblock.automap.AutoMapRule;
import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.automap.engine.AnimationMapper;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotCodec;
import com.beatblock.automap.engine.ParticleEvent;
import com.beatblock.automap.engine.RhythmEvent;
import com.beatblock.automap.engine.StructuralSection;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Timeline 特征轨与可选音乐结构段落构建 {@link ChoreographyPlan}。
 */
public final class ChoreographyPlanBuilder {

	private ChoreographyPlanBuilder() {}

	public static ChoreographyPlan fromTimeline(Timeline timeline, AutoMapConfig config) {
		return fromTimeline(timeline, config, null);
	}

	public static ChoreographyPlan fromTimeline(
		Timeline timeline,
		AutoMapConfig config,
		@Nullable List<StructuralSection> sections
	) {
		if (timeline == null || config == null) return ChoreographyPlan.empty();
		List<ChoreographyPlan.SectionPlan> sectionPlans = toSectionPlans(sections);

		List<ChoreographyPlan.StageRoleAssignment> roles = new ArrayList<>();
		for (Map.Entry<String, String> entry : config.getTargetByNormalizedFeature().entrySet()) {
			roles.add(new ChoreographyPlan.StageRoleAssignment(entry.getKey(), entry.getValue()));
		}

		List<ChoreographyPlan.MotionPhrase> motions = new ArrayList<>();
		for (var candidate : AutoMapGenerator.collectCandidates(timeline.getFeatureTracks(), config.getRules())) {
			AutoMapRule rule = candidate.rule();
			motions.add(new ChoreographyPlan.MotionPhrase(
				candidate.timeSeconds(),
				candidate.trackKey(),
				candidate.normalizedFeatureKey(),
				candidate.energy(),
				rule.getAnimationTypeId(),
				rule.getDurationSeconds(),
				rule.isUseEnergyForHeight(),
				rule.getHeightMultiplier(),
				resolveSectionIndex(sectionPlans, candidate.timeSeconds())
			));
		}

		return new ChoreographyPlan(
			sectionPlans,
			roles,
			motions,
			List.of(),
			List.of(),
			buildDensityCurve(sectionPlans, ChoreographyPlan.MusicalStructure.empty()),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty()
		);
	}

	/**
	 * 从完整 {@link MusicStructure}（Bar / Phrase / Section / Repeat）构建编舞计划。
	 */
	public static ChoreographyPlan fromMusicStructure(
		MusicStructure musicStructure,
		List<RhythmEvent> rhythmEvents,
		@Nullable List<CameraShot> cameraShots,
		@Nullable List<ParticleEvent> particleEvents,
		AutoMapStyle style,
		AutoMapConfig config
	) {
		if (musicStructure == null) {
			return fromRhythmAnalysis(rhythmEvents, null, cameraShots, particleEvents, style, config);
		}
		return fromRhythmAnalysis(
			rhythmEvents,
			musicStructure.sections(),
			cameraShots,
			particleEvents,
			style,
			config,
			musicStructure
		);
	}

	/**
	 * 从节奏分类结果（Smart Auto-Map 音频分析路径）构建编舞计划。
	 */
	public static ChoreographyPlan fromRhythmAnalysis(
		List<RhythmEvent> rhythmEvents,
		@Nullable List<StructuralSection> sections,
		@Nullable List<CameraShot> cameraShots,
		@Nullable List<ParticleEvent> particleEvents,
		AutoMapStyle style,
		AutoMapConfig config
	) {
		return fromRhythmAnalysis(
			rhythmEvents, sections, cameraShots, particleEvents, style, config, null);
	}

	private static ChoreographyPlan fromRhythmAnalysis(
		List<RhythmEvent> rhythmEvents,
		@Nullable List<StructuralSection> sections,
		@Nullable List<CameraShot> cameraShots,
		@Nullable List<ParticleEvent> particleEvents,
		AutoMapStyle style,
		AutoMapConfig config,
		@Nullable MusicStructure musicStructure
	) {
		if (rhythmEvents == null) rhythmEvents = List.of();
		if (config == null) config = AutoMapConfig.createDefault();

		List<ChoreographyPlan.StageRoleAssignment> roles = new ArrayList<>();
		for (Map.Entry<String, String> entry : config.getTargetByNormalizedFeature().entrySet()) {
			roles.add(new ChoreographyPlan.StageRoleAssignment(entry.getKey(), entry.getValue()));
		}

		Map<String, AutoMapRule> ruleByFeature = indexRules(config);
		List<ChoreographyPlan.SectionPlan> sectionPlans = toSectionPlans(sections);
		List<ChoreographyPlan.MotionPhrase> motions = new ArrayList<>(rhythmEvents.size());
		for (RhythmEvent event : rhythmEvents) {
			String normalized = AutoMapGenerator.normalizedFeatureKey(event.getType());
			String trackKey = event.getType().name().toLowerCase();
			AutoMapRule rule = ruleByFeature.get(normalized);
			boolean useHeight = rule != null && rule.isUseEnergyForHeight();
			float heightMult = rule != null ? rule.getHeightMultiplier() : config.getDefaultHeightMultiplier();
			motions.add(new ChoreographyPlan.MotionPhrase(
				event.getTimeSeconds(),
				trackKey,
				normalized,
				event.getEnergy(),
				AnimationMapper.getAnimationTypeId(event, style),
				AnimationMapper.getDurationSeconds(event.getType(), style),
				useHeight,
				heightMult,
				resolveSectionIndex(sectionPlans, event.getTimeSeconds())
			));
		}

		List<ChoreographyPlan.CameraPhrase> cameras = new ArrayList<>();
		if (cameraShots != null) {
			for (CameraShot shot : cameraShots) {
				cameras.add(CameraShotCodec.toPhrase(shot));
			}
		}

		List<ChoreographyVfx> vfx = new ArrayList<>();
		if (particleEvents != null) {
			List<String> targetIds = config.getTargetByNormalizedFeature().values().stream()
				.filter(id -> id != null && !id.isBlank())
				.toList();
			for (ParticleEvent event : particleEvents) {
				vfx.add(ChoreographyVfxFactory.fromParticleEvent(
					event,
					resolveSectionIndex(sectionPlans, event.getTimeSeconds()),
					targetIds
				));
			}
		}

		DensityCurve density;
		ChoreographyPlan.MusicalStructure musical;
		if (musicStructure != null) {
			musical = MusicalStructureMapper.fromAnalysis(musicStructure, sectionPlans);
			density = buildDensityCurve(sectionPlans, musical);
		} else {
			musical = ChoreographyPlan.MusicalStructure.empty();
			density = buildDensityCurve(sectionPlans, musical);
		}

		return new ChoreographyPlan(
			sectionPlans,
			roles,
			motions,
			cameras,
			vfx,
			density,
			List.of(),
			musical
		);
	}

	private static Map<String, AutoMapRule> indexRules(AutoMapConfig config) {
		Map<String, AutoMapRule> byFeature = new HashMap<>();
		for (AutoMapRule rule : config.getRules()) {
			byFeature.put(AutoMapGenerator.normalizeFeatureKey(rule.getFeatureKey()), rule);
		}
		return byFeature;
	}

	private static List<ChoreographyPlan.SectionPlan> toSectionPlans(@Nullable List<StructuralSection> sections) {
		if (sections == null || sections.isEmpty()) return List.of();
		List<ChoreographyPlan.SectionPlan> out = new ArrayList<>(sections.size());
		for (StructuralSection section : sections) {
			out.add(new ChoreographyPlan.SectionPlan(
				section.getStartSeconds(),
				section.getEndSeconds(),
				section.getType(),
				section.getLabel()
			));
		}
		return out;
	}

	private static DensityCurve buildDensityCurve(
		List<ChoreographyPlan.SectionPlan> sections,
		ChoreographyPlan.MusicalStructure musical
	) {
		DensityCurve base = buildDensityCurve(sections);
		if (musical.phrases().isEmpty()) return base;
		List<DensityCurve.Point> points = new ArrayList<>(base.points());
		for (ChoreographyPlan.MusicalPhrasePlan phrase : musical.phrases()) {
			double mid = (phrase.startSeconds() + phrase.endSeconds()) * 0.5;
			double sectionDensity = base.sampleAt(mid);
			double noveltyBoost = (1.0 - phrase.repetitionScore()) * 0.12;
			points.add(new DensityCurve.Point(mid, Math.min(1.0, sectionDensity + noveltyBoost)));
		}
		return DensityCurve.ofPoints(points);
	}

	private static DensityCurve buildDensityCurve(List<ChoreographyPlan.SectionPlan> sections) {
		if (sections.isEmpty()) return DensityCurve.uniform(1.0);
		List<DensityCurve.Point> points = new ArrayList<>();
		for (ChoreographyPlan.SectionPlan section : sections) {
			double density = switch (section.sectionType()) {
				case INTRO, OUTRO -> 0.25;
				case VERSE, BREAK, BRIDGE -> 0.45;
				case PRE_CHORUS -> 0.55;
				case BUILD -> 0.65;
				case CHORUS -> 0.85;
				case DROP -> 0.95;
			};
			points.add(new DensityCurve.Point(section.startSeconds(), density));
		}
		return DensityCurve.ofPoints(points);
	}

	private static int resolveSectionIndex(List<ChoreographyPlan.SectionPlan> sections, double timeSeconds) {
		for (int i = 0; i < sections.size(); i++) {
			ChoreographyPlan.SectionPlan section = sections.get(i);
			boolean withinEnd = i == sections.size() - 1
				? timeSeconds <= section.endSeconds()
				: timeSeconds < section.endSeconds();
			if (timeSeconds >= section.startSeconds() && withinEnd) {
				return i;
			}
		}
		return -1;
	}
}
