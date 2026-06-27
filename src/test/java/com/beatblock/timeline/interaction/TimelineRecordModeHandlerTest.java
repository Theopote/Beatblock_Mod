package com.beatblock.timeline.interaction;

import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class TimelineRecordModeHandlerTest {

	@Test
	void recordAtPlayheadWritesManualBlockAnimationEvent() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(10.0);
		TimelineEditor editor = new TimelineEditor(timeline);
		editor.getClock().seek(2.5);
		editor.getClock().play();

		StageObjectSystem objects = new StageObjectSystem();
		objects.register(StageObjectSystem.fromSelectionSnapshot(
			"obj_a",
			"Obj A",
			List.of(new BlockPos(0, 64, 0)),
			GroupSortingStrategy.SEQUENTIAL,
			0.0
		));

		TimelineToolbarState toolbar = new TimelineToolbarState();
		toolbar.setSnapToGrid(false);
		toolbar.setSnapToBeat(false);

		var outcome = TimelineRecordModeHandler.recordAtPlayhead(
			timeline, editor, toolbar, objects, true);

		assertTrue(outcome.success());
		assertEquals(1, timeline.getBlockAnimationEvents().size());
		assertEquals(2.5, timeline.getBlockAnimationEvents().getFirst().getTimeSeconds(), 1e-9);
		assertEquals("obj_a", timeline.getBlockAnimationEvents().getFirst().getTargetObjectId());
	}

	@Test
	void recordFailsWithoutStageObject() {
		Timeline timeline = Timeline.createDefault();
		TimelineEditor editor = new TimelineEditor(timeline);
		editor.getClock().play();

		var outcome = TimelineRecordModeHandler.recordAtPlayhead(
			timeline, editor, new TimelineToolbarState(), new StageObjectSystem(), true);

		assertFalse(outcome.success());
		assertEquals("no-stage-object", outcome.message());
	}
}
