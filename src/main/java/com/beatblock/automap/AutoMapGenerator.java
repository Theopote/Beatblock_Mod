package com.beatblock.automap;

import com.beatblock.automap.engine.RhythmType;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanBuilder;
import com.beatblock.automap.choreography.ChoreographyPlanCompiler;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 根据特征轨事件与规则生成时间线动画草稿，经编舞计划中间层写入（可 Undo）。
 */
public final class AutoMapGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(AutoMapGenerator.class);

	public static int generate(Timeline timeline, AutoMapConfig config, boolean replace) {
		if (timeline == null || config == null) return 0;

		Map<String, FeatureTrack> tracks = timeline.getFeatureTracks();
		if (tracks.isEmpty()) {
			LOGGER.info("BeatBlock Smart Auto Map: 无特征轨事件，请先导入音乐");
			return 0;
		}

		ChoreographyPlan plan = ChoreographyPlanBuilder.fromTimeline(timeline, config);
		int count = ChoreographyPlanCompiler.compileAnimationEvents(timeline, plan, config, replace);

		String fallbackTarget = resolveTargetObjectId();
		String targetLabel = fallbackTarget.isBlank() ? "(unbound)" : fallbackTarget;
		LOGGER.info("BeatBlock Smart Auto Map: 已生成 {} 个动画草稿（默认目标: {}）", count, targetLabel);
		return count;
	}

	public static void clearAutoAnimationEvents(Timeline timeline) {
		if (timeline == null) return;
		timeline.clearAutoAnimationEvents();
	}

	public static List<AutoMapCandidate> collectCandidates(
		Map<String, FeatureTrack> tracks,
		List<AutoMapRule> rules
	) {
		List<AutoMapCandidate> candidates = new ArrayList<>();
		for (Map.Entry<String, FeatureTrack> entry : tracks.entrySet()) {
			String trackKey = entry.getKey();
			FeatureTrack track = entry.getValue();
			if (track == null || track.getEvents().isEmpty()) continue;
			String normalizedKey = normalizeFeatureKey(trackKey);
			for (FeatureEvent event : track.getEvents()) {
				AutoMapRule rule = findRule(rules, trackKey, event.getEnergy());
				if (rule == null) continue;
				candidates.add(new AutoMapCandidate(
					event.getTimeSeconds(),
					trackKey,
					normalizedKey,
					event.getEnergy(),
					rule
				));
			}
		}
		return candidates;
	}

	public static AutoMapRule findRule(List<AutoMapRule> rules, String featureKey, float energy) {
		String normalized = normalizeFeatureKey(featureKey);
		for (AutoMapRule r : rules) {
			if (normalizeFeatureKey(r.getFeatureKey()).equals(normalized) && energy >= r.getMinEnergy()) {
				return r;
			}
		}
		return null;
	}

	public static String normalizeFeatureKey(String key) {
		if (key == null) return "low";
		return switch (key.toLowerCase()) {
			case "kick", "bass", "sub", "low", "drums" -> "low";
			case "snare", "snare_hi", "mid" -> "mid";
			case "hihat", "hat", "hihat_open", "high" -> "high";
			default -> key.toLowerCase();
		};
	}

	public static String normalizedFeatureKey(RhythmType rhythmType) {
		if (rhythmType == null) return "low";
		return switch (rhythmType) {
			case KICK -> "low";
			case SNARE -> "mid";
			case HIHAT -> "high";
		};
	}

	/**
	 * 解析目标舞台对象 id；无可用目标时返回空字符串（UNBOUND 事件，与 {@link com.beatblock.automap.engine.TimelineBuilder} 一致）。
	 */
	public static String resolveTargetObjectId() {
		var stageManager = com.beatblock.BeatBlock.getContext().stageManager();
		if (stageManager != null && stageManager.getCurrentStage().isPresent()) {
			return stageManager.getCurrentStage().get().getId();
		}
		var engine = com.beatblock.BeatBlock.getContext().blockAnimationEngine();
		if (engine != null) {
			var sys = engine.getStageObjectSystem();
			var all = sys != null ? sys.getAll() : null;
			if (all != null && !all.isEmpty()) {
				return all.iterator().next().getId();
			}
		}
		return "";
	}
}
