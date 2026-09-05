package com.beatblock.engine.layer;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.command.CutTimelineEventsCommand;
import com.beatblock.timeline.command.DeleteEventCommand;
import com.beatblock.timeline.command.layer.BindLayerToTrackCommand;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildLayerBindingSupportTest {

	private StageObjectSystem stageObjectSystem;
	private BuildLayerManager layerManager;
	private Timeline timeline;

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		stageObjectSystem = new StageObjectSystem();
		layerManager = new BuildLayerManager(stageObjectSystem);
		timeline = Timeline.createDefault();
	}

	@Test
	void deleteBindingEventUnbindsLayerAndUndoRestores() {
		BuildLayer layer = layerManager.createFromSelection("Tower", List.of(new BlockPos(0, 64, 0)));
		assertNotNull(layer);
		assertEquals(LayerVisibilityState.FREE_HIDDEN, layer.getState());

		BindLayerToTrackCommand bind = new BindLayerToTrackCommand(
			timeline, layerManager, layer.getId(), null, 1.0, 2.0);
		bind.execute();
		assertTrue(bind.isApplied());
		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, layer.getState());
		String clipId = layer.getBoundClipId();
		assertNotNull(clipId);

		Track track = findClipTrack(clipId);
		assertNotNull(track);
		var clip = track.getClip(clipId);
		assertNotNull(clip);
		assertEquals(1, clip.getEvents().size());
		var event = clip.getEvents().getFirst();

		DeleteEventCommand delete = new DeleteEventCommand(
			timeline, layerManager, track.getId(), clipId, event);
		delete.execute();

		assertEquals(LayerVisibilityState.FREE_HIDDEN, layer.getState());
		assertNull(layer.getBoundClipId());
		assertTrue(clip.getEvents().isEmpty());

		delete.undo();
		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, layer.getState());
		assertEquals(clipId, layer.getBoundClipId());
		assertEquals(1, clip.getEvents().size());
	}

	@Test
	void cutBindingEventUnbindsLayer() {
		BuildLayer layer = layerManager.createFromSelection("Tower", List.of(new BlockPos(1, 64, 0)));
		assertNotNull(layer);

		BindLayerToTrackCommand bind = new BindLayerToTrackCommand(
			timeline, layerManager, layer.getId(), null, 0.5, 2.0);
		bind.execute();
		assertTrue(bind.isApplied());
		String clipId = layer.getBoundClipId();
		assertNotNull(clipId);

		Track track = findClipTrack(clipId);
		assertNotNull(track);
		var clip = track.getClip(clipId);
		String eventId = clip.getEvents().getFirst().getId();

		SelectionState selection = new SelectionState();
		selection.selectEvent(eventId);
		var clipboard = new ArrayList<TimelineInteractionClipboard.ClipboardEvent>();

		CutTimelineEventsCommand cut = new CutTimelineEventsCommand(
			timeline, layerManager, selection, new TimelineTrackListState(), clipboard);
		cut.execute();

		assertEquals(LayerVisibilityState.FREE_HIDDEN, layer.getState());
		assertNull(layer.getBoundClipId());

		cut.undo();
		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, layer.getState());
		assertEquals(clipId, layer.getBoundClipId());
	}

	@Test
	void reconcileClearsDanglingBoundWithoutClip() {
		RuntimeStageObject stage = StageObjectSystem.fromBlocks("s1", "L", List.of(new BlockPos(2, 64, 0)));
		BuildLayer layer = new BuildLayer(
			"layer-1", "L", stage, LayerVisibilityState.BOUND_TO_TRACK, Map.of(), "missing-clip");
		layerManager.registerRestored(layer);

		int adjusted = BuildLayerBindingSupport.reconcileBindings(layerManager, timeline);
		assertEquals(1, adjusted);
		assertEquals(LayerVisibilityState.FREE_HIDDEN, layer.getState());
		assertNull(layer.getBoundClipId());
	}

	@Test
	void reconcileClearsOrphanBoundClipIdOnFreeLayer() {
		RuntimeStageObject stage = StageObjectSystem.fromBlocks("s2", "F", List.of(new BlockPos(3, 64, 0)));
		BuildLayer layer = new BuildLayer(
			"layer-2", "F", stage, LayerVisibilityState.FREE_HIDDEN, Map.of(), "stale-clip");
		layerManager.registerRestored(layer);

		int adjusted = BuildLayerBindingSupport.reconcileBindings(layerManager, timeline);
		assertEquals(1, adjusted);
		assertNull(layer.getBoundClipId());
		assertEquals(LayerVisibilityState.FREE_HIDDEN, layer.getState());
	}

	@Test
	void reconcileKeepsValidBinding() {
		BuildLayer layer = layerManager.createFromSelection("Keep", List.of(new BlockPos(4, 64, 0)));
		assertNotNull(layer);
		new BindLayerToTrackCommand(timeline, layerManager, layer.getId(), null, 0, 2).execute();
		String clipId = layer.getBoundClipId();

		int adjusted = BuildLayerBindingSupport.reconcileBindings(layerManager, timeline);
		assertEquals(0, adjusted);
		assertEquals(LayerVisibilityState.BOUND_TO_TRACK, layer.getState());
		assertEquals(clipId, layer.getBoundClipId());
	}

	private Track findClipTrack(String clipId) {
		for (Track track : BuildLayerTrackSupport.listTracks(timeline)) {
			if (track.getClip(clipId) != null) return track;
		}
		return timeline.getTrack(BuildLayerTrackSupport.DEFAULT_FIRST_TRACK_ID);
	}
}
