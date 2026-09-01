package com.beatblock.automap.choreography;

import com.beatblock.audio.analysis.structure.BarGridBuilder;
import com.beatblock.audio.analysis.structure.MusicStructure;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps {@link MusicStructure} analysis output into {@link ChoreographyPlan.MusicalStructure}.
 */
public final class MusicalStructureMapper {

	private MusicalStructureMapper() {}

	public static ChoreographyPlan.MusicalStructure fromAnalysis(
		MusicStructure structure,
		List<ChoreographyPlan.SectionPlan> sectionPlans
	) {
		if (structure == null) {
			return ChoreographyPlan.MusicalStructure.empty();
		}
		List<ChoreographyPlan.BarPlan> bars = toBarPlans(structure.bars(), sectionPlans);
		List<ChoreographyPlan.MusicalPhrasePlan> rawPhrases = toPhrasePlans(structure.phrases(), sectionPlans);
		List<ChoreographyPlan.MusicalPhrasePlan> phrases = RepeatGroupBuilder.annotateRepeatAnchors(rawPhrases);
		List<ChoreographyPlan.RepeatGroup> repeats = RepeatGroupBuilder.buildFromAnnotated(phrases);
		return new ChoreographyPlan.MusicalStructure(bars, phrases, repeats, structure.beatTimes());
	}

	private static List<ChoreographyPlan.BarPlan> toBarPlans(
		List<BarGridBuilder.BarSpan> bars,
		List<ChoreographyPlan.SectionPlan> sectionPlans
	) {
		if (bars == null || bars.isEmpty()) return List.of();
		List<ChoreographyPlan.BarPlan> out = new ArrayList<>(bars.size());
		for (BarGridBuilder.BarSpan bar : bars) {
			double mid = (bar.startSeconds() + bar.endSeconds()) * 0.5;
			out.add(new ChoreographyPlan.BarPlan(
				bar.startSeconds(),
				bar.endSeconds(),
				bar.barIndex(),
				resolveSectionIndex(sectionPlans, mid)
			));
		}
		return out;
	}

	private static List<ChoreographyPlan.MusicalPhrasePlan> toPhrasePlans(
		List<MusicStructure.PhraseSpan> phrases,
		List<ChoreographyPlan.SectionPlan> sectionPlans
	) {
		if (phrases == null || phrases.isEmpty()) return List.of();
		List<ChoreographyPlan.MusicalPhrasePlan> out = new ArrayList<>(phrases.size());
		for (MusicStructure.PhraseSpan phrase : phrases) {
			double mid = (phrase.startSeconds() + phrase.endSeconds()) * 0.5;
			out.add(new ChoreographyPlan.MusicalPhrasePlan(
				phrase.startSeconds(),
				phrase.endSeconds(),
				phrase.phraseIndex(),
				resolveSectionIndex(sectionPlans, mid),
				phrase.repetitionScore(),
				-1
			));
		}
		return out;
	}

	public static int resolveSectionIndex(List<ChoreographyPlan.SectionPlan> sections, double timeSeconds) {
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
