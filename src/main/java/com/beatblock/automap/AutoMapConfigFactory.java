package com.beatblock.automap;

import com.beatblock.BeatBlock;
import com.beatblock.automap.cast.StageCast;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapSettingsStore;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.PatternGenerator;
import com.beatblock.engine.RuntimeStageObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 从运行时舞台对象或 {@link AutoMapSettings} 构建 {@link AutoMapConfig}。
 */
public final class AutoMapConfigFactory {

	private AutoMapConfigFactory() {}

	/** 工具栏 Quick Auto Map：默认规则 + 会话目标映射（未配置时回退已注册舞台对象顺序）。 */
	public static AutoMapConfig forToolbar() {
		AutoMapConfig defaults = AutoMapConfig.createDefault();
		AutoMapConfig.Builder builder = AutoMapConfig.builder()
			.minGapSeconds(defaults.getMinGapSeconds())
			.defaultHeightMultiplier(defaults.getDefaultHeightMultiplier());
		for (AutoMapRule rule : defaults.getRules()) {
			builder.rule(rule);
		}
		applyTargetMapping(builder, AutoMapSettingsStore.current());
		return builder.build();
	}

	public static AutoMapConfig fromSettings(AutoMapSettings settings) {
		if (settings == null) {
			return AutoMapConfig.createDefault();
		}
		Complexity complexity = settings.getComplexity();
		PatternGenerator.FeatureMinGaps gaps = PatternGenerator.featureMinGaps(settings);

		AutoMapConfig defaults = AutoMapConfig.createDefault();
		AutoMapConfig.Builder builder = AutoMapConfig.builder()
			.minGapSeconds(PatternGenerator.getMinGapSeconds(complexity))
			.defaultHeightMultiplier(defaults.getDefaultHeightMultiplier());
		for (AutoMapRule rule : defaults.getRules()) {
			builder.rule(withMinGap(rule, PatternGenerator.minGapForFeature(
				AutoMapGenerator.normalizeFeatureKey(rule.getFeatureKey()), gaps)));
		}
		applyTargetMapping(builder, settings);
		return builder.build();
	}

	private static void applyTargetMapping(AutoMapConfig.Builder builder, AutoMapSettings settings) {
		List<String> ids = settings != null ? settings.getTargetObjectIds() : List.of();
		if (!ids.isEmpty()) {
			applyTargetIds(builder, ids);
			return;
		}
		applyRegisteredStageTargets(builder);
	}

	private static void applyTargetIds(AutoMapConfig.Builder builder, List<String> ids) {
		StageCast cast = StageCast.fromTargetIds(ids);
		for (var entry : cast.featureTargetMap().entrySet()) {
			builder.targetForFeature(entry.getKey(), entry.getValue());
		}
		// Ensure every cast member appears as a stage role for Phrase/Hero participants
		for (String id : cast.allObjectIds()) {
			if (id == null || id.isBlank()) continue;
			if (!cast.featureTargetMap().containsValue(id)) {
				builder.targetForFeature("cast:" + id, id);
			}
		}
	}

	private static AutoMapRule withMinGap(AutoMapRule rule, double minGapSeconds) {
		return new AutoMapRule(
			rule.getFeatureKey(),
			rule.getMinEnergy(),
			rule.getAnimationTypeId(),
			rule.getDurationSeconds(),
			rule.isUseEnergyForHeight(),
			rule.getHeightMultiplier(),
			minGapSeconds,
			rule.getTargetObjectId(),
			rule.getTimingSnap()
		);
	}

	private static void applyRegisteredStageTargets(AutoMapConfig.Builder builder) {
		var engine = BeatBlock.getContext().blockAnimationEngine();
		if (engine == null) return;
		var sys = engine.getStageObjectSystem();
		if (sys == null) return;
		List<RuntimeStageObject> stages = new ArrayList<>(sys.getAll());
		List<String> ids = new ArrayList<>();
		for (RuntimeStageObject stage : stages) {
			if (stage != null && stage.getId() != null && !stage.getId().isBlank()) {
				ids.add(stage.getId());
			}
		}
		applyTargetIds(builder, ids);
	}
}
