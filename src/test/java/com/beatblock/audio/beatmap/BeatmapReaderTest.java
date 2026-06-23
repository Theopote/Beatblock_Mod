package com.beatblock.audio.beatmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeatmapReaderTest {

	private static final String MINIMAL_BEATMAP = """
		{
		  "version": 1,
		  "meta": {
		    "source_file": "test.wav",
		    "duration_ms": 60000,
		    "bpm": 128,
		    "bpm_confidence": 0.95,
		    "time_signature": "4/4",
		    "sample_rate": 44100,
		    "generated_at": "2024-01-01",
		    "analyzer_version": "1.0"
		  },
		  "beats": [
		    {
		      "time_ms": 500,
		      "band": "kick",
		      "energy": 0.8,
		      "anchor": "arrive",
		      "beat_index": 0,
		      "bar_index": 0,
		      "beat_in_bar": 0
		    }
		  ],
		  "sections": [
		    {
		      "start_ms": 0,
		      "end_ms": 30000,
		      "label": "intro",
		      "energy_mean": 0.35
		    }
		  ],
		  "waveform_preview": {
		    "samples_per_second": 10,
		    "data": [0.1, 0.5, 0.2]
		  }
		}
		""";

	@Test
	void parseMinimalBeatmapJson() throws Exception {
		Beatmap beatmap = BeatmapReader.parse(MINIMAL_BEATMAP);

		assertEquals(1, beatmap.version);
		assertEquals("test.wav", beatmap.meta.sourceFile());
		assertEquals(128.0, beatmap.meta.bpm(), 1e-9);
		assertEquals(1, beatmap.beats.size());
		assertEquals("kick", beatmap.beats.getFirst().bandKey());
		assertEquals(1, beatmap.sections.size());
		assertEquals(SectionLabel.INTRO, beatmap.sections.getFirst().label());
		assertNotNull(beatmap.waveformPreview);
		assertEquals(3, beatmap.waveformPreview.data().length);
	}

	@Test
	void rejectsUnsupportedVersion() {
		String json = MINIMAL_BEATMAP.replace("\"version\": 1", "\"version\": 99");
		BeatmapReader.BeatmapVersionException ex = assertThrows(
			BeatmapReader.BeatmapVersionException.class,
			() -> BeatmapReader.parse(json)
		);
		assertEquals(99, ex.found);
		assertEquals(1, ex.expected);
	}

	@Test
	void rejectsInvalidJson() {
		assertThrows(BeatmapReader.BeatmapParseException.class,
			() -> BeatmapReader.parse("{not json"));
	}

	@Test
	void unknownSectionLabelMapsToUnknown() throws Exception {
		String json = MINIMAL_BEATMAP.replace("\"intro\"", "\"mystery_part\"");
		Beatmap beatmap = BeatmapReader.parse(json);
		assertEquals(SectionLabel.UNKNOWN, beatmap.sections.getFirst().label());
	}

	@Test
	void invalidAnchorThrowsParseException() {
		String json = MINIMAL_BEATMAP.replace("\"arrive\"", "\"invalid_anchor\"");
		BeatmapReader.BeatmapParseException ex = assertThrows(
			BeatmapReader.BeatmapParseException.class,
			() -> BeatmapReader.parse(json)
		);
		assertTrue(ex.getMessage().contains("beats"));
	}

	@Test
	void parsesStemWaveformsWhenPresent() throws Exception {
		String json = MINIMAL_BEATMAP.replace(
			"\"waveform_preview\"",
			"\"stem_waveforms\": {\"drums\": {\"samples_per_second\": 5, \"data\": [0.3, 0.7]}}, \"waveform_preview\""
		);
		Beatmap beatmap = BeatmapReader.parse(json);
		assertEquals(1, beatmap.stemWaveforms.size());
		assertEquals(2, beatmap.stemWaveforms.get("drums").data().length);
	}
}
