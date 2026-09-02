package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.SpatialMotifLayout;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhraseGrammarExpanderTest {

	@Test
	void kickEveryFourBeatsExpandsToLeftToRightCascade() {
		ChoreographyPhrase phrase = new ChoreographyPhrase(
			new TriggerSpec.EveryNBeats(4, "kick"),
			TargetSet.of("Tower_A", "Tower_B", "Tower_C", "Tower_D"),
			SpatialPatternSpec.leftToRight(),
			MotionPresetSpec.bounce(),
			TimingPatternSpec.stagger(0.08),
			IntensityEnvelope.crescendo(0.6f, 1.0f),
			VariationSpec.alternateHeight(0.3f),
			0
		);
		SpatialMotifLayout layout = new SpatialMotifLayout(Map.of(
			"Tower_A", new Vec3d(0, 64, 0),
			"Tower_B", new Vec3d(4, 64, 0),
			"Tower_C", new Vec3d(8, 64, 0),
			"Tower_D", new Vec3d(12, 64, 0)
		));
		PhraseTriggerContext context = new PhraseTriggerContext(List.of(
			new FeatureEventRef(0.0, "kick", 1.0f),
			new FeatureEventRef(1.0, "kick", 1.0f),
			new FeatureEventRef(2.0, "kick", 1.0f),
			new FeatureEventRef(3.0, "kick", 1.0f),
			new FeatureEventRef(4.0, "kick", 1.0f),
			new FeatureEventRef(5.0, "kick", 1.0f),
			new FeatureEventRef(6.0, "kick", 1.0f),
			new FeatureEventRef(7.0, "kick", 1.0f),
			new FeatureEventRef(8.0, "kick", 1.0f)
		));

		List<PhraseGrammarExpander.ExpandedPhraseEvent> events = PhraseGrammarExpander.expand(phrase, context, layout);

		assertEquals(12, events.size());
		assertEquals("Tower_A", events.get(0).targetObjectId());
		assertEquals("Tower_D", events.get(3).targetObjectId());
		assertEquals(0.0, events.get(0).timeSeconds(), 1e-9);
		assertEquals(0.24, events.get(3).timeSeconds(), 1e-9);
		assertEquals(4.0, events.get(4).timeSeconds(), 1e-9);
		assertEquals(8.0, events.get(8).timeSeconds(), 1e-9);
		assertEquals("bounce", events.get(0).primitiveId());
		assertTrue(events.get(0).params().containsKey("phraseGrammar"));
		assertTrue(events.get(8).energy() > events.get(0).energy());
	}
}
