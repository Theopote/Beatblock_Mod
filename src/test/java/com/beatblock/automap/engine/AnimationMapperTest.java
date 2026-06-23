package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationMapperTest {

	@Test
	void edmStyleMapsKickToJump() {
		assertEquals("jump", AnimationMapper.getAnimationTypeId(RhythmType.KICK, AutoMapStyle.EDM));
	}

	@Test
	void cinematicStyleMapsSnareToWave() {
		assertEquals("wave", AnimationMapper.getAnimationTypeId(RhythmType.SNARE, AutoMapStyle.CINEMATIC));
	}

	@Test
	void minimalStyleUsesShorterDurations() {
		assertEquals(0.35, AnimationMapper.getDurationSeconds(RhythmType.KICK, AutoMapStyle.MINIMAL), 1e-6);
	}

	@Test
	void kickDurationLongerThanHiHatForEdmStyle() {
		double kick = AnimationMapper.getDurationSeconds(RhythmType.KICK, AutoMapStyle.EDM);
		double hihat = AnimationMapper.getDurationSeconds(RhythmType.HIHAT, AutoMapStyle.EDM);
		assertTrue(kick > hihat);
	}
}
