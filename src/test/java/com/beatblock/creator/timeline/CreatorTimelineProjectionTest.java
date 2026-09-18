package com.beatblock.creator.timeline;

import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TrackDefinition;
import com.beatblock.ui.preferences.UiPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatorTimelineProjectionTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void enableCreatorView() {
		System.setProperty("beatblock.test.configDir", tempDir.toString());
		UiPreferences.setCreatorTimelineView(true);
	}

	@AfterEach
	void tearDown() {
		System.clearProperty("beatblock.test.configDir");
	}

	@Test
	void suppressesAutoAnimationRow() {
		assertTrue(CreatorTimelineProjection.suppressesRow(TimelineTrackMeta.ROW_ANIM_AUTO));
		assertFalse(CreatorTimelineProjection.suppressesRow(TimelineTrackMeta.ROW_ANIM_BLOCK));
	}

	@Test
	void buildRowOrderHidesAutoAndGroupsMusicFirst() {
		List<TrackDefinition> audio = List.of(
			new TrackDefinition("waveform", "Main Mix", TrackDefinition.VisualType.WAVEFORM, TrackDefinition.GROUP_NONE),
			new TrackDefinition("kick", "Kick", TrackDefinition.VisualType.IMPULSE, TrackDefinition.GROUP_RHYTHM)
		);
		List<Integer> order = CreatorTimelineProjection.buildRowOrder(audio, List.of(), List.of());

		int blockIndex = order.indexOf(TimelineTrackMeta.ROW_ANIM_BLOCK);
		int autoIndex = order.indexOf(TimelineTrackMeta.ROW_ANIM_AUTO);
		int waveformIndex = order.indexOf(TimelineTrackMeta.ROW_AUDIO_SUBS_START);
		int kickIndex = order.indexOf(TimelineTrackMeta.ROW_AUDIO_SUBS_START + 1);

		assertTrue(blockIndex >= 0);
		assertEquals(-1, autoIndex);
		assertTrue(waveformIndex < kickIndex);
		assertTrue(order.indexOf(TimelineTrackMeta.ROW_AUDIO_GROUP) < waveformIndex);
		assertTrue(order.indexOf(TimelineTrackMeta.ROW_ANIMATION_GROUP) < kickIndex);
	}

	@Test
	void creatorDisplayNameMapsPerformanceGroup() {
		String label = CreatorTimelineProjection.creatorDisplayName(
			TimelineTrackMeta.ROW_ACTION_GROUP, null, List.of(), List.of(), List.of());
		assertEquals(CreatorTimelineProjection.groupLabel(CreatorTrackGroup.PERFORMANCE), label);
	}

	@Test
	void performanceInteractionRowMapsAutoTrackToBlockRow() {
		assertEquals(
			TimelineTrackMeta.ROW_ANIM_BLOCK,
			CreatorTimelineProjection.performanceInteractionRowForTrackId("animation_auto"));
	}
}
