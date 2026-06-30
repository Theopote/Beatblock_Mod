package com.beatblock.timeline.rendering;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.ui.i18n.BBTexts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class TimelineTrackMetaTest {

	@Test
	void cameraRowFollowsBuildLayerSlots() {
		assertEquals(TimelineTrackMeta.ROW_BUILD_LAYER_END + 1, TimelineTrackMeta.ROW_CAMERA);
		assertEquals(TimelineTrackMeta.ROW_ACTION_GROUP, TimelineTrackMeta.getParentRowIndex(TimelineTrackMeta.ROW_CAMERA));
		assertEquals(
			BBTexts.get("beatblock.track.default.camera"),
			TimelineTrackMeta.getDefaultName(TimelineTrackMeta.ROW_CAMERA)
		);
	}

	@Test
	void audioAndAnimationFeatureSubRowSlots() {
		assertTrue(TimelineTrackMeta.isAudioSubRow(TimelineTrackMeta.ROW_AUDIO_SUBS_START));
		assertEquals(0, TimelineTrackMeta.audioSubRowSlot(TimelineTrackMeta.ROW_AUDIO_SUBS_START));
		assertFalse(TimelineTrackMeta.isAudioSubRow(TimelineTrackMeta.ROW_ACTION_GROUP));

		assertTrue(TimelineTrackMeta.isAnimationFeatureSubRow(TimelineTrackMeta.ROW_ANIM_FEATURES_START));
		assertEquals(0, TimelineTrackMeta.animationFeatureSubRowSlot(TimelineTrackMeta.ROW_ANIM_FEATURES_START));
	}

	@Test
	void categoryLabelsMatchRowKind() {
		assertEquals(
			BBTexts.get("beatblock.track.type.feature"),
			TimelineTrackMeta.getCategoryTypeLabel(TimelineTrackMeta.ROW_ANIMATION_GROUP)
		);
		assertEquals(
			BBTexts.get("beatblock.track.type.animation"),
			TimelineTrackMeta.getCategoryTypeLabel(TimelineTrackMeta.ROW_ANIM_BLOCK)
		);
		assertEquals(
			BBTexts.get("beatblock.track.type.event"),
			TimelineTrackMeta.getCategoryTypeLabel(TimelineTrackMeta.ROW_GLOBAL_EVENT)
		);
	}
}
