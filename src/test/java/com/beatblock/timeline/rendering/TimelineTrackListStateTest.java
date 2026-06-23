package com.beatblock.timeline.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineTrackListStateTest {

	@Test
	void effectivelyMutedWhenSelfMutedOrOtherSoloActive() {
		TimelineTrackListState state = new TimelineTrackListState();
		int row = TimelineTrackMeta.ROW_ANIM_BLOCK;
		int other = TimelineTrackMeta.ROW_ANIM_AUTO;

		state.setMuted(row, true);
		assertTrue(state.isEffectivelyMuted(row));

		state.setMuted(row, false);
		state.setSoloed(other, true);
		assertTrue(state.isEffectivelyMuted(row));
		assertFalse(state.isEffectivelyMuted(other));
	}

	@Test
	void customDisplayNameOverridesDefault() {
		TimelineTrackListState state = new TimelineTrackListState();
		int row = TimelineTrackMeta.ROW_CAMERA;
		state.setCustomName(row, "Hero Cam");
		assertEquals("Hero Cam", state.getDisplayName(row));
		state.clearCustomName(row);
		assertEquals(TimelineTrackMeta.getDefaultName(row), state.getDisplayName(row));
	}

	@Test
	void groupLockedPropagatesToAudioSubRows() {
		TimelineTrackListState state = new TimelineTrackListState();
		state.setLocked(TimelineTrackMeta.ROW_AUDIO_GROUP, true);
		assertTrue(state.isLocked(TimelineTrackMeta.ROW_AUDIO_SUBS_START));
	}

	@Test
	void trackHeaderWidthClampsToBounds() {
		TimelineTrackListState state = new TimelineTrackListState();
		state.setTrackHeaderWidth(10f);
		assertEquals(160f, state.getTrackHeaderWidth(), 1e-6f);
		state.setTrackHeaderWidth(999f);
		assertEquals(420f, state.getTrackHeaderWidth(), 1e-6f);
	}

	@Test
	void groupCollapseOnlyAppliesToGroupRows() {
		TimelineTrackListState state = new TimelineTrackListState();
		state.setGroupCollapsed(TimelineTrackMeta.ROW_ANIMATION_GROUP, true);
		assertTrue(state.isGroupCollapsed(TimelineTrackMeta.ROW_ANIMATION_GROUP));
		state.setGroupCollapsed(TimelineTrackMeta.ROW_CAMERA, true);
		assertFalse(state.isGroupCollapsed(TimelineTrackMeta.ROW_CAMERA));
	}
}
