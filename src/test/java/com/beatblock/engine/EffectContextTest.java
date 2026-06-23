package com.beatblock.engine;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectContextTest {

	@Test
	void paramDoubleParsesNumbersAndFallsBack() {
		EffectContext ctx = new EffectContext(Vec3d.ZERO, Map.of(
			"waveAmplitude", 0.75,
			"impactRadius", "2.5",
			"bad", "not-a-number"
		));
		assertEquals(0.75, ctx.paramDouble("waveAmplitude", 0.1), 1e-9);
		assertEquals(2.5, ctx.paramDouble("impactRadius", 0.1), 1e-9);
		assertEquals(0.1, ctx.paramDouble("bad", 0.1), 1e-9);
		assertEquals(1.0, ctx.paramDouble("missing", 1.0), 1e-9);
	}

	@Test
	void defaultsCenterToOriginWhenNull() {
		EffectContext ctx = new EffectContext(null);
		assertEquals(Vec3d.ZERO, ctx.getStageCenter());
	}
}
