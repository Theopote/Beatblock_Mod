package com.beatblock.automap.choreography;

import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Accent / Phrase / Hero 强度档位：控制编舞计划保留哪些语义层。
 * <p>
 * Quick Start 与 Smart Auto Map 共用此配置，避免「轻量模式」仍偷偷生成 Hero。
 */
public enum ChoreographyLayerProfile {
	/** 仅 Accent：局部 pulse 纹理，不含跨对象 Phrase / Hero。 */
	ACCENT_ONLY,
	/** Accent + Phrase：段内空间编舞，不含 Hero 高潮。 */
	PHRASE,
	/** Accent + Phrase + Hero：完整舞台强度。 */
	HERO_FULL;

	public boolean includeAccents() {
		return true;
	}

	public boolean includePhrases() {
		return this != ACCENT_ONLY;
	}

	public boolean includeHeroes() {
		return this == HERO_FULL;
	}

	/**
	 * 按档位裁剪计划中的动作 / Grammar Phrase / legacy Spatial Motif。
	 * 镜头与 VFX 仍由 {@link com.beatblock.automap.engine.AutoMapSettings} 开关控制。
	 */
	public ChoreographyPlan apply(@Nullable ChoreographyPlan plan) {
		if (plan == null) {
			return ChoreographyPlan.empty();
		}
		List<ChoreographyPlan.MotionPhrase> motions = includeAccents()
			? plan.motionPhrases()
			: List.of();
		List<SpatialMotifPhrase> spatial = includePhrases()
			? plan.spatialMotifPhrases()
			: List.of();
		List<ChoreographyPhrase> grammar = filterGrammar(plan.choreographyPhrases());
		if (motions == plan.motionPhrases()
			&& spatial == plan.spatialMotifPhrases()
			&& grammar == plan.choreographyPhrases()) {
			return plan;
		}
		return new ChoreographyPlan(
			plan.sections(),
			plan.stageRoles(),
			motions,
			plan.cameraPhrases(),
			plan.vfxPhrases(),
			plan.densityCurve(),
			plan.sectionEdits(),
			plan.musicalStructure(),
			spatial,
			grammar
		);
	}

	private List<ChoreographyPhrase> filterGrammar(List<ChoreographyPhrase> phrases) {
		if (phrases == null || phrases.isEmpty()) {
			return List.of();
		}
		if (includeHeroes() && includePhrases()) {
			return phrases;
		}
		if (!includePhrases() && !includeHeroes()) {
			return List.of();
		}
		List<ChoreographyPhrase> out = new ArrayList<>(phrases.size());
		for (ChoreographyPhrase phrase : phrases) {
			if (phrase == null) {
				continue;
			}
			boolean hero = phrase.isHero();
			if (hero && includeHeroes()) {
				out.add(phrase);
			} else if (!hero && includePhrases()) {
				out.add(phrase);
			}
		}
		return List.copyOf(out);
	}
}
