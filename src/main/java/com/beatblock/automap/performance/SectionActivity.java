package com.beatblock.automap.performance;

import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.automap.choreography.ChoreographyBudget;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 段落 / 乐句活动度：与 {@link ChoreographyBudget#sectionVisualDensity} 共用锚点，
 * 供 Accent 层 {@link com.beatblock.automap.engine.PatternGenerator} 做结构感知筛选。
 */
public final class SectionActivity {

	private static final double MIN_EFFECTIVE = 0.15;
	private static final double MAX_EFFECTIVE = 1.2;
	private static final double PHRASE_REPETITION_WEIGHT = 0.35;
	private static final double ENERGY_LIFT = 0.35;

	private SectionActivity() {}

	/** 段落活动度 0–1（DROP=1）。 */
	public static double forSectionType(@Nullable SectionType type) {
		return ChoreographyBudget.sectionVisualDensity(type);
	}

	/**
	 * 乐句活动度：重复越高越疏。
	 * {@code 1 - 0.35 × repetitionScore}
	 */
	public static double phraseActivity(double repetitionScore) {
		double r = Math.max(0.0, Math.min(1.0, repetitionScore));
		return 1.0 - PHRASE_REPETITION_WEIGHT * r;
	}

	/** {@code clamp(section × phrase, 0.15, 1.2)} */
	public static double effective(double sectionActivity, double phraseActivity) {
		return Math.max(MIN_EFFECTIVE, Math.min(MAX_EFFECTIVE, sectionActivity * phraseActivity));
	}

	/** 稀疏段落抬高能量门槛：{@code (1 - section) × 0.35}。 */
	public static float energyLift(double sectionActivity) {
		double s = Math.max(0.0, Math.min(1.0, sectionActivity));
		return (float) ((1.0 - s) * ENERGY_LIFT);
	}

	public static double sectionActivityAt(@Nullable MusicStructure structure, double timeSeconds) {
		StructuralSection section = sectionAt(structure, timeSeconds);
		return forSectionType(section != null ? section.getType() : null);
	}

	public static double phraseActivityAt(@Nullable MusicStructure structure, double timeSeconds) {
		if (structure == null || structure.phrases().isEmpty()) {
			return 1.0;
		}
		List<MusicStructure.PhraseSpan> phrases = structure.phrases();
		for (int i = 0; i < phrases.size(); i++) {
			MusicStructure.PhraseSpan phrase = phrases.get(i);
			boolean withinEnd = i == phrases.size() - 1
				? timeSeconds <= phrase.endSeconds()
				: timeSeconds < phrase.endSeconds();
			if (timeSeconds >= phrase.startSeconds() && withinEnd) {
				return phraseActivity(phrase.repetitionScore());
			}
		}
		return 1.0;
	}

	public static double effectiveAt(@Nullable MusicStructure structure, double timeSeconds) {
		return effective(sectionActivityAt(structure, timeSeconds), phraseActivityAt(structure, timeSeconds));
	}

	public static @Nullable StructuralSection sectionAt(@Nullable MusicStructure structure, double timeSeconds) {
		if (structure == null || structure.sections().isEmpty()) {
			return null;
		}
		List<StructuralSection> sections = structure.sections();
		for (int i = 0; i < sections.size(); i++) {
			StructuralSection section = sections.get(i);
			boolean withinEnd = i == sections.size() - 1
				? timeSeconds <= section.getEndSeconds()
				: timeSeconds < section.getEndSeconds();
			if (timeSeconds >= section.getStartSeconds() && withinEnd) {
				return section;
			}
		}
		return null;
	}
}
