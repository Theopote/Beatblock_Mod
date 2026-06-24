package com.beatblock.timeline.rendering;

import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapMeta;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineAudioDropHandlerTest {

	@Test
	void isBeatmapReadyWhenNoStemSeparation() {
		BeatmapMeta meta = new BeatmapMeta(
			"song.wav", 60_000, 120, 1.0, "4/4", 44_100, "", "", null, null, null);
		Beatmap beatmap = new Beatmap(1, meta, List.of(), List.of(), null, null);

		assertTrue(TimelineAudioDropHandler.isBeatmapReadyForImmediateApply(beatmap));
	}

	@Test
	void isBeatmapNotReadyWhenStemFileMissing() {
		BeatmapMeta meta = new BeatmapMeta(
			"song.wav", 60_000, 120, 1.0, "4/4", 44_100, "", "", null, "demucs",
			Map.of("drums", "drums.wav"));
		Beatmap beatmap = new Beatmap(1, meta, List.of(), List.of(), null, null);
		beatmap.beatmapFilePath = Path.of("nonexistent-dir/beatmap.json");

		assertFalse(TimelineAudioDropHandler.isBeatmapReadyForImmediateApply(beatmap));
	}
}
