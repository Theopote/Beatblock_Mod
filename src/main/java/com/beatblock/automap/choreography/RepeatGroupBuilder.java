package com.beatblock.automap.choreography;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Clusters musical phrases with high repetition scores into {@link ChoreographyPlan.RepeatGroup}s.
 */
public final class RepeatGroupBuilder {

	static final double REPEAT_THRESHOLD = 0.62;

	private RepeatGroupBuilder() {}

	public static List<ChoreographyPlan.RepeatGroup> buildFromAnnotated(
		List<ChoreographyPlan.MusicalPhrasePlan> phrases
	) {
		if (phrases == null || phrases.isEmpty()) return List.of();

		Map<Integer, List<Integer>> membersByAnchor = new HashMap<>();
		Map<Integer, Double> scoreByAnchor = new HashMap<>();
		for (ChoreographyPlan.MusicalPhrasePlan phrase : phrases) {
			if (phrase.repeatAnchorPhraseIndex() < 0) continue;
			int anchor = phrase.repeatAnchorPhraseIndex();
			membersByAnchor.computeIfAbsent(anchor, ignored -> new ArrayList<>()).add(phrase.phraseIndex());
			scoreByAnchor.merge(anchor, phrase.repetitionScore(), Math::max);
		}

		List<ChoreographyPlan.RepeatGroup> groups = new ArrayList<>();
		int groupId = 0;
		for (Map.Entry<Integer, List<Integer>> entry : membersByAnchor.entrySet()) {
			int anchor = entry.getKey();
			LinkedHashSet<Integer> unique = new LinkedHashSet<>();
			unique.add(anchor);
			unique.addAll(entry.getValue());
			List<Integer> indices = new ArrayList<>(unique);
			indices.sort(Integer::compareTo);
			groups.add(new ChoreographyPlan.RepeatGroup(
				groupId++,
				anchor,
				indices,
				scoreByAnchor.getOrDefault(anchor, REPEAT_THRESHOLD)
			));
		}
		return groups;
	}

	static List<ChoreographyPlan.MusicalPhrasePlan> annotateRepeatAnchors(
		List<ChoreographyPlan.MusicalPhrasePlan> phrases
	) {
		if (phrases == null || phrases.isEmpty()) return List.of();
		List<ChoreographyPlan.MusicalPhrasePlan> out = new ArrayList<>(phrases.size());
		for (int i = 0; i < phrases.size(); i++) {
			ChoreographyPlan.MusicalPhrasePlan phrase = phrases.get(i);
			int anchor = -1;
			if (phrase.repetitionScore() >= REPEAT_THRESHOLD) {
				anchor = findRepeatAnchor(phrases, i);
			}
			out.add(new ChoreographyPlan.MusicalPhrasePlan(
				phrase.startSeconds(),
				phrase.endSeconds(),
				phrase.phraseIndex(),
				phrase.sectionIndex(),
				phrase.repetitionScore(),
				anchor
			));
		}
		return out;
	}

	private static int findRepeatAnchor(List<ChoreographyPlan.MusicalPhrasePlan> phrases, int phraseIndex) {
		ChoreographyPlan.MusicalPhrasePlan current = phrases.get(phraseIndex);
		int best = -1;
		double bestScore = -1;
		for (int i = 0; i < phraseIndex; i++) {
			ChoreographyPlan.MusicalPhrasePlan prior = phrases.get(i);
			if (current.sectionIndex() >= 0 && prior.sectionIndex() != current.sectionIndex()) {
				continue;
			}
			double score = prior.repetitionScore();
			if (score > bestScore) {
				bestScore = score;
				best = prior.phraseIndex();
			}
		}
		if (best >= 0) return best;
		return phraseIndex > 0 ? phrases.get(0).phraseIndex() : -1;
	}
}
