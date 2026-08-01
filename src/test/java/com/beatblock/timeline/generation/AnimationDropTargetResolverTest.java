package com.beatblock.timeline.generation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationDropTargetResolverTest {

	@Test
	void unboundWhenNothingSelectedAndNoRegisteredObjects() {
		var result = AnimationDropTargetResolver.resolve(List.of(), List.of(), List.of());
		assertEquals(AnimationDropTargetResolver.Mode.UNBOUND, result.mode());
		assertTrue(result.isUnbound());
		assertEquals(List.of(""), result.targetsForEventCreation());
	}

	@Test
	void unboundWhenManyRegisteredButNoSelection() {
		// Must NOT silently pick first of many
		var result = AnimationDropTargetResolver.resolve(
			List.of(),
			List.of(),
			List.of("a", "b", "c")
		);
		assertEquals(AnimationDropTargetResolver.Mode.UNBOUND, result.mode());
		assertTrue(result.isUnbound());
	}

	@Test
	void singleRegisteredObjectAutoBinds() {
		var result = AnimationDropTargetResolver.resolve(List.of(), List.of(), List.of("only"));
		assertEquals(AnimationDropTargetResolver.Mode.SINGLE, result.mode());
		assertEquals(List.of("only"), result.targetObjectIds());
		assertFalse(result.isUnbound());
	}

	@Test
	void preferredSelectionWinsOverEventsAndRegistered() {
		var result = AnimationDropTargetResolver.resolve(
			List.of("layer-stage"),
			List.of("from-event"),
			List.of("ambient")
		);
		assertEquals(AnimationDropTargetResolver.Mode.SINGLE, result.mode());
		assertEquals(List.of("layer-stage"), result.targetObjectIds());
	}

	@Test
	void multiPreferredCreatesMultiMode() {
		var result = AnimationDropTargetResolver.resolve(
			List.of("a", "b"),
			List.of(),
			List.of("a", "b", "c")
		);
		assertEquals(AnimationDropTargetResolver.Mode.MULTI, result.mode());
		assertEquals(List.of("a", "b"), result.targetsForEventCreation());
	}

	@Test
	void selectedEventTargetsUsedWhenNoPreferred() {
		java.util.ArrayList<String> fromEvents = new java.util.ArrayList<>();
		fromEvents.add("ev-a");
		fromEvents.add("ev-a");
		fromEvents.add("  ");
		fromEvents.add(null);
		var result = AnimationDropTargetResolver.resolve(
			List.of(),
			fromEvents,
			List.of("x", "y")
		);
		assertEquals(AnimationDropTargetResolver.Mode.SINGLE, result.mode());
		assertEquals(List.of("ev-a"), result.targetObjectIds());
	}

	@Test
	void multiEventTargetsWithoutPreferred() {
		var result = AnimationDropTargetResolver.resolve(
			List.of(),
			List.of("t1", "t2"),
			List.of("t1", "t2", "t3")
		);
		assertEquals(AnimationDropTargetResolver.Mode.MULTI, result.mode());
		assertEquals(2, result.targetsForEventCreation().size());
	}

	@Test
	void isUnboundTargetHelpers() {
		assertTrue(AnimationDropTargetResolver.isUnboundTarget(null));
		assertTrue(AnimationDropTargetResolver.isUnboundTarget(""));
		assertTrue(AnimationDropTargetResolver.isUnboundTarget("  "));
		assertFalse(AnimationDropTargetResolver.isUnboundTarget("stage-1"));
	}
}
