package com.beatblock.audio;

import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioLoaderTest {

	@Test
	void loadFromDecodedUsesInjectedContext() {
		Timeline timeline = Timeline.createDefault();
		MusicPlayer musicPlayer = new MusicPlayer();
		TimelineEditor editor = new TimelineEditor(timeline, musicPlayer);
		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();
		AudioLoader loader = new AudioLoader(() -> context);

		DecodedAudio audio = new DecodedAudio(new float[]{0.1f, -0.2f, 0.3f}, 44100, 3.0 / 44100.0);
		loader.loadFromDecoded(audio);

		assertTrue(loader.isLoaded("(decoded)"));
		assertEquals("(decoded)", timeline.getMetadata("audioPath"));
		assertEquals(3.0 / 44100.0, musicPlayer.getDurationSeconds(), 1e-6);
	}
}
