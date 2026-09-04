package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;
import org.jspecify.annotations.Nullable;

/**
 * 编舞生成预算：在 minGap 之上限制每拍事件数、并发舞台对象、并行 Phrase 套数与 Hero 次数。
 * <p>
 * {@link #maxPhraseLayers()} 限制同一拍内并行的 Phrase/Hero <em>instance</em> 数量
 * （例如 Wave + Cascade），不是 {@link ChoreographyLayer} 枚举种类数；Accent 不占此名额。
 * <p>
 * 由 {@link DensityCurve#budgetAt(double)} 按时间采样；段落默认密度见 {@link #sectionVisualDensity(SectionType)}。
 */
public record ChoreographyBudget(
	int maxEventsPerBeat,
	int maxConcurrentStageObjects,
	int maxPhraseLayers,
	double maxVisualDensity,
	int maxHeroMomentsPerSection
) {
	public static final double DEFAULT_BEAT_SECONDS = 0.5;

	public ChoreographyBudget {
		maxEventsPerBeat = Math.max(1, maxEventsPerBeat);
		maxConcurrentStageObjects = Math.max(1, maxConcurrentStageObjects);
		maxPhraseLayers = Math.max(1, Math.min(3, maxPhraseLayers));
		maxVisualDensity = Math.max(0.0, Math.min(1.0, maxVisualDensity));
		maxHeroMomentsPerSection = Math.max(0, maxHeroMomentsPerSection);
	}

	/** 按 0–1 视觉密度推导预算上限。 */
	public static ChoreographyBudget forDensity(double density) {
		double d = Math.max(0.0, Math.min(1.0, density));
		int maxEvents = Math.max(1, (int) Math.round(1 + 11 * d));
		int maxObjects = Math.max(1, (int) Math.round(1 + 7 * d));
		int maxLayers = d < 0.35 ? 1 : (d < 0.70 ? 2 : 3);
		int maxHero = d < 0.50 ? 0 : (d < 0.85 ? 1 : 2);
		return new ChoreographyBudget(maxEvents, maxObjects, maxLayers, d, maxHero);
	}

	public static ChoreographyBudget forSectionType(@Nullable SectionType sectionType) {
		return forDensity(sectionVisualDensity(sectionType));
	}

	/**
	 * 段落默认视觉密度（生成预算的锚点，与 {@link DensityCurve} 构建一致）。
	 */
	public static double sectionVisualDensity(@Nullable SectionType sectionType) {
		if (sectionType == null) return 0.5;
		return switch (sectionType) {
			case INTRO, OUTRO -> 0.20;
			case VERSE, BREAK, BRIDGE -> 0.40;
			case PRE_CHORUS -> 0.55;
			case BUILD -> 0.65;
			case CHORUS -> 0.75;
			case DROP -> 1.00;
		};
	}
}
