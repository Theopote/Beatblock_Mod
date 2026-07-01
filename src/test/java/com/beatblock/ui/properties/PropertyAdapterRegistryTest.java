package com.beatblock.ui.properties;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.properties.adapters.AnimationEventPropertyAdapter;
import com.beatblock.ui.properties.adapters.CameraPropertyAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@Test
	void cameraClipWithoutEventUsesCameraAdapter() {
		Track track = new Track(Timeline.TRACK_ID_CAMERA, "Camera", TrackType.CAMERA);
		Clip clip = new Clip("cam-clip", 0.0, 8.0);
		EventPropertiesRef ref = new EventPropertiesRef(track, clip, null);

		assertTrue(TimelinePropertyKinds.isCameraRef(ref));
		assertFalse(TimelinePropertyKinds.isAnimationRef(ref));
	}
}
