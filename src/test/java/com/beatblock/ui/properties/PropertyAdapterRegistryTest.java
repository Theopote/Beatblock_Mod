package com.beatblock.ui.properties;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.properties.adapters.AnimationEventPropertyAdapter;
import com.beatblock.ui.properties.adapters.AudioClipPropertyAdapter;
import com.beatblock.ui.properties.adapters.BuildLayerClipPropertyAdapter;
import com.beatblock.ui.properties.adapters.CameraPropertyAdapter;
import com.beatblock.ui.properties.adapters.GlobalEventPropertyAdapter;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyAdapterRegistryTest {

	@BeforeEach
	void setUp() {
		PropertyAdapterBootstrap.resetForTests();
		PropertyAdapterBootstrap.initialize();
	}

	@AfterEach
	void tearDown() {
		PropertyAdapterBootstrap.resetForTests();
	}

	@Test
	void resolvesAnimationAdapterForAnimationEvent() {
		TimelineEvent event = new TimelineEvent("evt-1", 0.0, EventType.ANIMATION, null);
		Track track = new Track("track-a", "Animation", TrackType.ANIMATION);
		Clip clip = new Clip("clip-a", 0.0, 4.0);
		clip.addEvent(event);
		EventPropertiesRef ref = new EventPropertiesRef(track, clip, event);

		TimelinePropertyContext ctx = new TimelinePropertyContext(
			ref,
			Timeline.createDefault(),
			null,
			new SelectionState(),
			null
		);

		assertInstanceOf(AnimationEventPropertyAdapter.class, PropertyAdapterRegistry.getAdapterFor(ctx));
	}

	@Test
	void resolvesCameraAdapterForCameraKeyframe() {
		TimelineEvent event = new TimelineEvent("cam-kf", 1.0, EventType.CAMERA_KEYFRAME, null);
		Track track = new Track(Timeline.TRACK_ID_CAMERA, "Camera", TrackType.CAMERA);
		Clip clip = new Clip("cam-clip", 0.0, 8.0);
		clip.addEvent(event);
		EventPropertiesRef ref = new EventPropertiesRef(track, clip, event);

		TimelinePropertyContext ctx = new TimelinePropertyContext(
			ref,
			Timeline.createDefault(),
			null,
			new SelectionState(),
			null
		);

		assertInstanceOf(CameraPropertyAdapter.class, PropertyAdapterRegistry.getAdapterFor(ctx));
	}

	@Test
	void resolvesGlobalAdapterForGlobalEvent() {
		TimelineEvent event = new TimelineEvent("global-1", 2.0, EventType.GLOBAL, java.util.Map.of("type", "STAGE", "name", "Intro"));
		Track track = new Track(Timeline.TRACK_ID_GLOBAL, "Global", TrackType.EVENT);
		Clip clip = new Clip("global-clip", 2.0, 2.1);
		clip.addEvent(event);
		EventPropertiesRef ref = new EventPropertiesRef(track, clip, event);

		TimelinePropertyContext ctx = new TimelinePropertyContext(
			ref,
			Timeline.createDefault(),
			null,
			new SelectionState(),
			null
		);

		assertInstanceOf(GlobalEventPropertyAdapter.class, PropertyAdapterRegistry.getAdapterFor(ctx));
	}

	@Test
	void resolvesAudioAdapterForSelectedAudioClip() {
		Timeline timeline = Timeline.createDefault();
		Track audio = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		Clip clip = TimelineOperations.addClip(audio, 0.0, 8.0);
		EventPropertiesRef ref = new EventPropertiesRef(audio, clip, null);

		TimelinePropertyContext ctx = new TimelinePropertyContext(
			ref,
			timeline,
			null,
			new SelectionState(),
			null
		);

		assertInstanceOf(AudioClipPropertyAdapter.class, PropertyAdapterRegistry.getAdapterFor(ctx));
	}

	@Test
	void resolvesBuildLayerAdapterForClipOnlySelection() {
		Timeline timeline = Timeline.createDefault();
		Track track = new Track(BuildLayerTrackSupport.DEFAULT_FIRST_TRACK_ID, "Build 1", TrackType.BUILD_LAYER);
		timeline.addTrack(track);
		Clip clip = TimelineOperations.addClip(track, 1.0, 3.0);
		EventPropertiesRef ref = new EventPropertiesRef(track, clip, null);

		assertTrue(TimelinePropertyKinds.isBuildLayerClipRef(ref));
		TimelinePropertyContext ctx = new TimelinePropertyContext(
			ref,
			timeline,
			null,
			new SelectionState(),
			null
		);
		assertInstanceOf(BuildLayerClipPropertyAdapter.class, PropertyAdapterRegistry.getAdapterFor(ctx));
	}

	@Test
	void returnsNullWhenNothingSelected() {
		TimelinePropertyContext ctx = new TimelinePropertyContext(
			null,
			Timeline.createDefault(),
			null,
			new SelectionState(),
			null
		);

		assertNull(PropertyAdapterRegistry.getAdapterFor(ctx));
	}
}
