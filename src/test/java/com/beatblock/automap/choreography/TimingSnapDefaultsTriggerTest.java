package com.beatblock.automap.choreography;

import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.choreography.grammar.IntensityEnvelope;
import com.beatblock.automap.choreography.grammar.MotionPresetSpec;
import com.beatblock.automap.choreography.grammar.SpatialPatternSpec;
import com.beatblock.automap.choreography.grammar.TargetSet;
import com.beatblock.automap.choreography.grammar.TimingPatternSpec;
import com.beatblock.automap.choreography.grammar.TriggerSpec;
import com.beatblock.automap.choreography.grammar.VariationSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimingSnapDefaultsTriggerTest {

	@Test
	void forTriggerMapsFeatureTriggersToNoneAndBeatGridToBeat() {
		assertEquals(ChoreographyTimingSnap.NONE, TimingSnapDefaults.forTrigger(new TriggerSpec.OnFeature("kick")));
		assertEquals(ChoreographyTimingSnap.NONE, TimingSnapDefaults.forTrigger(new TriggerSpec.FirstFeature("kick", 0.7f)));
		assertEquals(ChoreographyTimingSnap.NONE, TimingSnapDefaults.forTrigger(new TriggerSpec.EveryNFeatureHits("kick", 4)));
		assertEquals(ChoreographyTimingSnap.BEAT, TimingSnapDefaults.forTrigger(new TriggerSpec.EveryNBeats(4, 0)));
	}

	@Test
	void choreographyPhraseDefaultsTimingSnapFromTrigger() {
		ChoreographyPhrase onFeature = phrase(new TriggerSpec.OnFeature("kick"));
		ChoreographyPhrase everyBeats = phrase(new TriggerSpec.EveryNBeats(4));
		ChoreographyPhrase everyHits = phrase(new TriggerSpec.EveryNFeatureHits("kick", 4));

		assertEquals(ChoreographyTimingSnap.NONE, onFeature.timingSnap());
		assertEquals(ChoreographyTimingSnap.BEAT, everyBeats.timingSnap());
		assertEquals(ChoreographyTimingSnap.NONE, everyHits.timingSnap());
	}

	private static ChoreographyPhrase phrase(TriggerSpec trigger) {
		return new ChoreographyPhrase(
			trigger,
			TargetSet.of("a", "b"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.flat(0.8f),
			VariationSpec.none(),
			0
		);
	}
}
