package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackTest {

	@Test
	void audioTrackCreatesAudioData() {
		Track track = new Track("audio-1", "Main", TrackType.AUDIO);
		assertNotNull(track.getAudioData());
		assertTrue(track.isEnabled());
	}

	@Test
	void animationTrackHasNoAudioDataByDefault() {
		Track track = new Track("anim-1", "Anim", TrackType.ANIMATION);
		assertNull(track.getAudioData());
	}

	@Test
	void removeClipById() {
		Track track = new Track("t1", "T", TrackType.ANIMATION);
		Clip clip = new Clip("c1", 0, 1);
		track.addClip(clip);
		assertTrue(track.removeClip("c1"));
		assertFalse(track.removeClip("missing"));
		assertTrue(track.getClips().isEmpty());
	}

	@Test
	void getClipFindsAddedClip() {
		Track track = new Track("t1", "Anim", TrackType.ANIMATION);
		Clip clip = new Clip("clip-a", 0, 2);
		track.addClip(clip);
		assertEquals(clip, track.getClip("clip-a"));
	}

	@Test
	void enabledFlagCanBeToggled() {
		Track track = new Track("t1", "T", TrackType.EVENT);
		assertTrue(track.isEnabled());
		track.setEnabled(false);
		assertFalse(track.isEnabled());
	}
}
