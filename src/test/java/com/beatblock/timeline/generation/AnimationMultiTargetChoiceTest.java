package com.beatblock.timeline.generation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationMultiTargetChoiceTest {

	@Test
	void primaryTakesFirstOnly() {
		assertEquals(
			List.of("a"),
			AnimationMultiTargetChoice.expand(List.of("a", "b", "c"), AnimationMultiTargetChoice.PRIMARY)
		);
	}

	@Test
	void allKeepsEveryTarget() {
		assertEquals(
			List.of("a", "b", "c"),
			AnimationMultiTargetChoice.expand(List.of("a", "b", "c"), AnimationMultiTargetChoice.ALL)
		);
	}

	@Test
	void emptyCandidatesYieldUnboundPlaceholder() {
		assertEquals(List.of(""), AnimationMultiTargetChoice.expand(List.of(), AnimationMultiTargetChoice.ALL));
		assertEquals(List.of(""), AnimationMultiTargetChoice.expand(null, AnimationMultiTargetChoice.PRIMARY));
	}

	@Test
	void nullChoiceDefaultsToAll() {
		assertEquals(
			List.of("x", "y"),
			AnimationMultiTargetChoice.expand(List.of("x", "y"), null)
		);
	}
}
