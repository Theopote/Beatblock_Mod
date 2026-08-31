package com.beatblock.automap;

import com.beatblock.BeatBlock;
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
		if (ids.size() >= 1) builder.targetForFeature("low", ids.get(0));
		if (ids.size() >= 2) builder.targetForFeature("mid", ids.get(1));
		if (ids.size() >= 3) builder.targetForFeature("high", ids.get(2));
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
			rule.getTargetObjectId()
		);
	}

	private static void applyRegisteredStageTargets(AutoMapConfig.Builder builder) {
		var engine = BeatBlock.getContext().blockAnimationEngine();
		if (engine == null) return;
		var sys = engine.getStageObjectSystem();
		if (sys == null) return;
		List<RuntimeStageObject> stages = new ArrayList<>(sys.getAll());
		if (stages.size() >= 1) builder.targetForFeature("low", stages.get(0).getId());
		if (stages.size() >= 2) builder.targetForFeature("mid", stages.get(1).getId());
		if (stages.size() >= 3) builder.targetForFeature("high", stages.get(2).getId());
	}
}
