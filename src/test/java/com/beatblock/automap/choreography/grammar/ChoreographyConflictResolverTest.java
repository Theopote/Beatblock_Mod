package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyLayer;
import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.MotifAxis;
import com.beatblock.automap.choreography.SpatialMotifId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChoreographyConflictResolverTest {

	@Test
	void heroSuppressesAccentOnSharedTargetAndTime() {
		ChoreographyPhraseInstance hero = phrase(
			"hero:0",
			ChoreographyLayer.HERO,
			1.0,
			List.of("Tower_A", "Tower_B"),
			1.0f,
			0.5
		);
		ChoreographyPhraseInstance accent = accent("accent:0", "Tower_A", 1.05, 0.4f);

		List<ChoreographyPhraseInstance> kept = ChoreographyConflictResolver.resolve(List.of(accent, hero));

		assertEquals(Set.of("hero:0"), ids(kept));
	}

	@Test
	void phraseAndAccentOnDifferentTargetsBothKept() {
		ChoreographyPhraseInstance phrase = phrase(
			"phrase:0",
			ChoreographyLayer.PHRASE,
			1.0,
			List.of("Tower_A", "Tower_B", "Tower_C"),
			0.8f,
			0.5
		);
		ChoreographyPhraseInstance accent = accent("accent:0", "Tower_D", 1.0, 0.5f);

		List<ChoreographyPhraseInstance> kept = ChoreographyConflictResolver.resolve(List.of(phrase, accent));

		assertEquals(Set.of("phrase:0", "accent:0"), ids(kept));
	}

	@Test
	void heroSuppressesOverlappingPhraseOnSharedTarget() {
		ChoreographyPhraseInstance hero = phrase(
			"hero:0",
			ChoreographyLayer.HERO,
			2.0,
			List.of("A", "B", "C", "D"),
			1.0f,
			0.6
		);
		ChoreographyPhraseInstance wave = phrase(
			"phrase:wave",
			ChoreographyLayer.PHRASE,
			2.1,
			List.of("A", "B"),
			0.9f,
			0.5
		);

		List<ChoreographyPhraseInstance> kept = ChoreographyConflictResolver.resolve(List.of(wave, hero));

		assertEquals(Set.of("hero:0"), ids(kept));
	}

	@Test
	void nonOverlappingSameTargetBothKept() {
		ChoreographyPhraseInstance early = accent("accent:early", "Tower_A", 1.0, 0.5f);
		ChoreographyPhraseInstance late = accent("accent:late", "Tower_A", 3.0, 0.5f);

		List<ChoreographyPhraseInstance> kept = ChoreographyConflictResolver.resolve(List.of(early, late));

		assertEquals(2, kept.size());
		assertTrue(ids(kept).containsAll(Set.of("accent:early", "accent:late")));
	}

	@Test
	void sharesTargetAndTimeOverlapHelpers() {
		ChoreographyPhraseInstance phrase = phrase(
			"phrase:0",
			ChoreographyLayer.PHRASE,
			1.0,
			List.of("A", "B"),
			0.8f,
			0.5
		);
		ChoreographyPhraseInstance accentSame = accent("accent:same", "A", 1.2, 0.3f);
		ChoreographyPhraseInstance accentOther = accent("accent:other", "Z", 1.2, 0.3f);

		assertTrue(ChoreographyConflictResolver.sharesTarget(phrase, accentSame));
		assertFalse(ChoreographyConflictResolver.sharesTarget(phrase, accentOther));
		assertTrue(ChoreographyConflictResolver.timeOverlaps(phrase, accentSame));
	}

	private static Set<String> ids(List<ChoreographyPhraseInstance> instances) {
		return instances.stream().map(ChoreographyPhraseInstance::instanceId).collect(Collectors.toSet());
	}

	private static ChoreographyPhraseInstance accent(
		String id,
		String target,
		double time,
		float intensity
	) {
		return ChoreographyPhraseInstance.accent(
			id,
			"kick",
			0,
			0,
			time,
			target,
			new MotionPresetSpec("pulse", 0.4, true, 2f),
			intensity,
			ChoreographyTimingSnap.BEAT
		);
	}

	private static ChoreographyPhraseInstance phrase(
		String id,
		ChoreographyLayer layer,
		double time,
		List<String> targets,
		float intensity,
		double duration
	) {
		return new ChoreographyPhraseInstance(
			id,
			"CASCADE",
			layer,
			0,
			0,
			time,
			SpatialPatternSpec.of(SpatialMotifId.CASCADE, MotifAxis.X),
			new MotionPresetSpec("bounce", duration, true, 4f),
			targets,
			intensity,
			ChoreographyPhraseInstance.priorityFor(layer, intensity),
			ChoreographyTimingSnap.BEAT,
			new TimingPatternSpec.Simultaneous(),
			VariationSpec.none(),
			0
		);
	}
}
