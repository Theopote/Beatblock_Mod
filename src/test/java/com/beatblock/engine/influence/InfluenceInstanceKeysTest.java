package com.beatblock.engine.influence;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.EngineAnimationInstance;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceInstanceKeysTest {

	@Test
	void keyCombinesTargetTimeAndDefinition() {
		var def = new AnimationDefinition(BlockInfluencePresets.get("Pulse"));
		var target = StageObjectSystem.fromBlocks("stage-a", "Stage", List.of(new BlockPos(0, 64, 0)));
		var instance = new EngineAnimationInstance(def, target, 2.5, 3.0, 1f);

		String key = InfluenceInstanceKeys.key(instance);
		assertTrue(key.startsWith("stage-a@2.5#Pulse"));
	}

	@Test
	void unknownWhenInstanceIncomplete() {
		assertEquals("unknown", InfluenceInstanceKeys.key(null));
	}
}
