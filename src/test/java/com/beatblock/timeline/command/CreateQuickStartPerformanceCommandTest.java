package com.beatblock.timeline.command;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.ui.presenter.QuickStartTimelineSnapshot;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateQuickStartPerformanceCommandTest {

	@Test
	void compositeContainsStageChoreographyCameraAndVfx() {
		Timeline timeline = Timeline.createDefault();
		StageObjectSystem objects = new StageObjectSystem();
		QuickStartTimelineSnapshot before = QuickStartTimelineSnapshot.capture(timeline);

		RuntimeStageObject created = StageObjectSystem.fromBlocks(
			"building_1",
			"Building 1",
			List.of(new BlockPos(0, 64, 0))
		);
		objects.register(created);

		var anim = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		assertNotNull(anim);
		Clip clip = new Clip("c1", 0, 8);
		clip.addEvent(new TimelineEvent("e1", 1.0, EventType.ANIMATION, Map.of()));
		anim.addClip(clip);

		var camera = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		assertNotNull(camera);
		camera.addClip(new Clip("cam", 0, 4));

		var global = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		assertNotNull(global);
		global.addClip(new Clip("vfx", 0, 4));

		QuickStartTimelineSnapshot after = QuickStartTimelineSnapshot.capture(timeline);
		CreateQuickStartPerformanceCommand command = CreateQuickStartPerformanceCommand.alreadyApplied(
			timeline, objects, before, after, created, true, true
		);
		assertEquals(4, command.commandCount());
		assertTrue(command.includesCamera());
		assertTrue(command.includesVfx());

		CommandManager manager = new CommandManager();
		manager.execute(command);
		assertNotNull(objects.get("building_1"));
		assertFalse(anim.getClips().isEmpty());

		manager.undo();
		assertNull(objects.get("building_1"));
		assertTrue(timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClips().isEmpty());
		assertTrue(timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().isEmpty());
		assertTrue(timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().isEmpty());

		manager.redo();
		assertNotNull(objects.get("building_1"));
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO).getClips().size());
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_CAMERA).getClips().size());
		assertEquals(1, timeline.getTrack(Timeline.TRACK_ID_GLOBAL).getClips().size());
	}

	@Test
	void describeUsesQuickStartUndoLabel() {
		CreateQuickStartPerformanceCommand command = CreateQuickStartPerformanceCommand.alreadyApplied(
			null, null, QuickStartTimelineSnapshot.capture(null), QuickStartTimelineSnapshot.capture(null),
			null, false, false
		);
		String description = CommandDescriptions.describe(command);
		assertTrue(
			description.contains("performance")
				|| description.contains("表演")
				|| description.contains("quick_start_generate"),
			() -> "unexpected description: " + description
		);
	}
}
