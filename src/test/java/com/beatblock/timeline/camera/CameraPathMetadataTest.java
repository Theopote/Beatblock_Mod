package com.beatblock.timeline.camera;

import com.beatblock.timeline.Timeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraPathMetadataTest {

	@Test
	void defaultsToVisibleWhenUnset() {
		Timeline timeline = Timeline.createDefault();
		assertTrue(CameraPathMetadata.isPathVisible(timeline, "clip-a"));
	}

	@Test
	void setPathVisibleRoundTrips() {
		Timeline timeline = Timeline.createDefault();
		CameraPathMetadata.setPathVisible(timeline, "clip-a", false);
		assertFalse(CameraPathMetadata.isPathVisible(timeline, "clip-a"));

		CameraPathMetadata.setPathVisible(timeline, "clip-a", true);
		assertTrue(CameraPathMetadata.isPathVisible(timeline, "clip-a"));
	}

	@Test
	void treatsFalseLikeStringsAsHidden() {
		Timeline timeline = Timeline.createDefault();
		timeline.setMetadata("cameraPathVisible_clip-b", "false");
		assertFalse(CameraPathMetadata.isPathVisible(timeline, "clip-b"));
		timeline.setMetadata("cameraPathVisible_clip-b", "0");
		assertFalse(CameraPathMetadata.isPathVisible(timeline, "clip-b"));
	}
}
