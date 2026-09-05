package com.beatblock.timeline.project;

import com.beatblock.timeline.Timeline;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineClipMetadataPersistenceTest {

	@Test
	void roundTripsAudioClipMetadataAndClearsStaleKeys() {
		Timeline original = Timeline.createDefault();
		original.setMetadata("clipLabel_a", "Intro");
		original.setMetadata("clipAudioPath_a", "C:/a.wav");
		original.setMetadata("clipAudioKey_a", "key-a");
		original.setMetadata("audioRootClipId", "a");
		original.setMetadata("audioAssetId", "asset-a");
		original.setMetadata("unrelated", "keep-me");

		JsonObject json = TimelineClipMetadataPersistence.toJson(original);
		assertTrue(json.has("clipLabel_a"));
		assertFalse(json.has("unrelated"));

		Timeline restored = Timeline.createDefault();
		restored.setMetadata("clipLabel_old", "stale");
		restored.setMetadata("unrelated", "session");
		TimelineClipMetadataPersistence.loadInto(restored, json);

		assertEquals("Intro", restored.getMetadata("clipLabel_a"));
		assertEquals("C:/a.wav", restored.getMetadata("clipAudioPath_a"));
		assertEquals("key-a", restored.getMetadata("clipAudioKey_a"));
		assertEquals("a", restored.getMetadata("audioRootClipId"));
		assertEquals("asset-a", restored.getMetadata("audioAssetId"));
		assertNull(restored.getMetadata("clipLabel_old"));
		assertEquals("session", restored.getMetadata("unrelated"));
	}
}
