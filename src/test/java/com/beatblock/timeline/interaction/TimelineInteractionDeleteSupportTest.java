package com.beatblock.timeline.interaction;

import com.beatblock.engine.StageObject;
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
import static org.junit.jupiter.api.Assertions.assertNull;

class TimelineInteractionDeleteSupportTest {

	@Test
	void deletingBoundClipUnbindsLayer() {
		Fixture fixture = fixture();
		fixture.selection().selectClip(fixture.clip().getId());

		TimelineInteractionDeleteSupport.deleteSelectedEntries(
			fixture.timeline(), fixture.selection(), new TimelineTrackListState(), fixture.manager());

		assertNull(fixture.track().getClip(fixture.clip().getId()));
		assertEquals(LayerVisibilityState.FREE_HIDDEN, fixture.layer().getState());
		assertNull(fixture.layer().getBoundClipId());
	}

	@Test
	void deletingBindingEventUnbindsLayer() {
		Fixture fixture = fixture();
		fixture.selection().selectEvent(fixture.event().getId());

		TimelineInteractionDeleteSupport.deleteSelectedEntries(
			fixture.timeline(), fixture.selection(), new TimelineTrackListState(), fixture.manager());

		assertNull(fixture.clip().getEvent(fixture.event().getId()));
		assertEquals(LayerVisibilityState.FREE_HIDDEN, fixture.layer().getState());
		assertNull(fixture.layer().getBoundClipId());
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
		StageObject stage = StageObjectSystem.fromBlocks(
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
