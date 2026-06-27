package com.beatblock.engine.influence;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.EffectContext;
import com.beatblock.engine.EngineAnimationInstance;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactVfxTrackerTest {

	@Test
	void contributeFiresOnceWhenThresholdCrossed() {
		BlockInfluencePreset preset = BlockInfluencePresets.get("RhythmDrop");
		StageObject target = StageObjectSystem.fromBlocks("t1", "Target", List.of(new BlockPos(0, 64, 0)));
		AnimationDefinition def = new AnimationDefinition(preset);
		EngineAnimationInstance instance = new EngineAnimationInstance(
			def, target, 0.0, 1.0, 1f, Map.of("impactThreshold", 0.92, "impactVfxKind", "rhythm_impact")
		);
		EffectContext ctx = new EffectContext(Vec3d.ZERO, instance.getExtraParams());
		InfluenceFrame frame = new InfluenceFrame();
		ImpactVfxTracker tracker = new ImpactVfxTracker();

		tracker.contribute("k1", instance, preset, frame, 0.90f, 0.88f, ctx);
		assertTrue(frame.getVfxTriggers().isEmpty());

		tracker.contribute("k1", instance, preset, frame, 0.93f, 0.90f, ctx);
		assertEquals(1, frame.getVfxTriggers().size());
		assertEquals("rhythm_impact", frame.getVfxTriggers().getFirst().kind());

		frame = new InfluenceFrame();
		tracker.contribute("k1", instance, preset, frame, 0.95f, 0.94f, ctx);
		assertTrue(frame.getVfxTriggers().isEmpty());
	}
}
