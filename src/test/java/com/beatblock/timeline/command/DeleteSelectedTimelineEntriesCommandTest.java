package com.beatblock.timeline.command;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeleteSelectedTimelineEntriesCommandTest {

	@Test
	void deletingBoundClipIsUndoable() {
		Fixture f = fixture();
		f.selection().selectClip(f.clip().getId());

		DeleteSelectedTimelineEntriesCommand cmd = new DeleteSelectedTimelineEntriesCommand(
			f.timeline(), f.manager(), f.selection(), new TimelineTrackListState());
		cmd.execute();

		assertNull(f.track().getClip(f.clip().getId()));
		assertEquals(LayerVisibilityState.FREE_HIDDEN, f.layer().getState());
		assertNull(f.layer().getBoundClipId());

		cmd.undo();
		assertNotNull(f.track().getClip(f.clip().getId()));
		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, f.layer().getState());
		assertEquals(f.clip().getId(), f.layer().getBoundClipId());
		assertNotNull(f.track().getClip(f.clip().getId()).getEvent(f.event().getId()));
	}

	@Test
	void deletingBoundClipRedoWorksAfterSelectionCleared() {
		Fixture f = fixture();
		f.selection().selectClip(f.clip().getId());

		DeleteSelectedTimelineEntriesCommand cmd = new DeleteSelectedTimelineEntriesCommand(
			f.timeline(), f.manager(), f.selection(), new TimelineTrackListState());
		cmd.execute();
		cmd.undo();
		f.selection().clearAll();
		cmd.execute();

		assertNull(f.track().getClip(f.clip().getId()));
		assertEquals(LayerVisibilityState.FREE_HIDDEN, f.layer().getState());
	}

	@Test
	void deletingBindingEventRemovesEmptyClipAndUndoRestores() {
		Fixture f = fixture();
		f.selection().selectEvent(f.event().getId());

		DeleteSelectedTimelineEntriesCommand cmd = new DeleteSelectedTimelineEntriesCommand(
			f.timeline(), f.manager(), f.selection(), new TimelineTrackListState());
		cmd.execute();

		assertNull(f.track().getClip(f.clip().getId()));
		assertEquals(LayerVisibilityState.FREE_HIDDEN, f.layer().getState());

		cmd.undo();
		Clip restored = f.track().getClip(f.clip().getId());
		assertNotNull(restored);
		assertNotNull(restored.getEvent(f.event().getId()));
		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, f.layer().getState());
		assertEquals(f.clip().getId(), f.layer().getBoundClipId());
	}

	private static Fixture fixture() {
		Timeline timeline = new Timeline();
		Track track = new Track("build_layer_1", "Build Layer", TrackType.BUILD_LAYER);
		Clip clip = new Clip("clip-layer", 0.0, 2.0);
		TimelineEvent event = new TimelineEvent(
			"event-layer", 0.0, EventType.ANIMATION, Map.of("layerId", "layer-one"));
		clip.addEvent(event);
		track.addClip(clip);
		timeline.addTrack(track);

		BuildLayerManager manager = new BuildLayerManager(new StageObjectSystem());
		RuntimeStageObject stage = StageObjectSystem.fromBlocks(
			"stage-one", "Layer", List.of(new BlockPos(0, 64, 0)));
		BuildLayer layer = new BuildLayer(
			"layer-one", "Layer", stage, LayerVisibilityState.BOUND_TO_TRACK, Map.of(), clip.getId());
		manager.registerRestored(layer);
		return new Fixture(timeline, track, clip, event, manager, layer, new SelectionState());
	}

	private record Fixture(
		Timeline timeline, Track track, Clip clip, TimelineEvent event,
		BuildLayerManager manager, BuildLayer layer, SelectionState selection
	) {}
}
