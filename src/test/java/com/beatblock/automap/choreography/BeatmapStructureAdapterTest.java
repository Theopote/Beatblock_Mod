package com.beatblock.automap.choreography;

import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.audio.beatmap.AnchorType;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.audio.beatmap.BeatmapMeta;
import com.beatblock.audio.beatmap.MusicSection;
import com.beatblock.audio.beatmap.SectionLabel;
import com.beatblock.automap.engine.SectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BeatmapStructureAdapterTest {

	@Test
	void mapsSectionsAndBarsFromBeatmap() {
		Beatmap beatmap = new Beatmap(
			1,
			new BeatmapMeta("song.wav", 16000, 120, 1.0, "4/4", 44100, "", "", null, null, null),
			List.of(
				new BeatEvent(0, "kick", 0.8f, AnchorType.ARRIVE, 0, 0, 0),
				new BeatEvent(500, "snare", 0.7f, AnchorType.ARRIVE, 1, 0, 1),
				new BeatEvent(2000, "kick", 0.8f, AnchorType.ARRIVE, 2, 1, 0),
				new BeatEvent(2500, "snare", 0.7f, AnchorType.ARRIVE, 3, 1, 1)
			),
			List.of(
				new MusicSection(0, 8000, SectionLabel.INTRO, 0.3f),
				new MusicSection(8000, 16000, SectionLabel.CHORUS, 0.8f)
			),
			null,
			null
		);

		MusicStructure structure = BeatmapStructureAdapter.fromBeatmap(beatmap);

		assertEquals(16.0, structure.durationSeconds(), 1e-6);
		assertEquals(2, structure.sections().size());
		assertEquals(SectionType.INTRO, structure.sections().get(0).getType());
		assertEquals(SectionType.CHORUS, structure.sections().get(1).getType());
		assertFalse(structure.bars().isEmpty());
		assertEquals(0, structure.bars().getFirst().barIndex());
		assertFalse(structure.phrases().isEmpty());
		assertEquals(2, structure.phrases().size());
	}

	@Test
	void buildPhrasesFromBarsChunksByFourBarsWithinSection() {
		var bars = List.of(
			new com.beatblock.audio.analysis.structure.BarGridBuilder.BarSpan(0, 2, 0),
			new com.beatblock.audio.analysis.structure.BarGridBuilder.BarSpan(2, 4, 1),
			new com.beatblock.audio.analysis.structure.BarGridBuilder.BarSpan(4, 6, 2),
			new com.beatblock.audio.analysis.structure.BarGridBuilder.BarSpan(6, 8, 3),
			new com.beatblock.audio.analysis.structure.BarGridBuilder.BarSpan(8, 10, 4),
			new com.beatblock.audio.analysis.structure.BarGridBuilder.BarSpan(10, 12, 5),
			new com.beatblock.audio.analysis.structure.BarGridBuilder.BarSpan(12, 14, 6),
			new com.beatblock.audio.analysis.structure.BarGridBuilder.BarSpan(14, 16, 7)
		);
		var sections = List.of(
			new com.beatblock.automap.engine.StructuralSection(0, 16, SectionType.VERSE)
		);

		var phrases = BeatmapStructureAdapter.buildPhrasesFromBars(bars, sections, 16.0);

		assertEquals(2, phrases.size());
		assertEquals(0.0, phrases.get(0).startSeconds(), 1e-6);
		assertEquals(8.0, phrases.get(0).endSeconds(), 1e-6);
		assertEquals(8.0, phrases.get(1).startSeconds(), 1e-6);
		assertEquals(16.0, phrases.get(1).endSeconds(), 1e-6);
	}
}
