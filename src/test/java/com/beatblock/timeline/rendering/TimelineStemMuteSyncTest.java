package com.beatblock.timeline.rendering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineStemMuteSyncTest {

	private static TrackDefinition td(String key) {
		return new TrackDefinition(key, key, TrackDefinition.VisualType.WAVEFORM, TrackDefinition.GROUP_NONE);
	}

	@Test
	void mapsStemWaveformKeyToStemName() {
		assertTrue(TimelineStemMuteSync.mapsToStemAudioControl("stem_wf_drums", "drums"));
		assertFalse(TimelineStemMuteSync.mapsToStemAudioControl("stem_wf_drums", "bass"));
		assertFalse(TimelineStemMuteSync.mapsToStemAudioControl("waveform", "drums"));
	}

	@Test
	void isPlayableAudioControlRowForWaveformAndStemSlots() {
		List<TrackDefinition> subs = List.of(td("waveform"), td("stem_wf_bass"));
		int wfRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START;
		int stemRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START + 1;

		assertTrue(TimelineStemMuteSync.isPlayableAudioControlRow(wfRow, subs));
		assertTrue(TimelineStemMuteSync.isPlayableAudioControlRow(stemRow, subs));
		assertFalse(TimelineStemMuteSync.isPlayableAudioControlRow(TimelineTrackMeta.ROW_CAMERA, subs));
	}

	@Test
	void rowEffectivelyMutedWhenDirectlyMuted() {
		TimelineTrackListState state = new TimelineTrackListState();
		List<TrackDefinition> subs = List.of(td("waveform"));
		int row = TimelineTrackMeta.ROW_AUDIO_SUBS_START;
		state.setMuted(row, true);

		assertTrue(TimelineStemMuteSync.isPlayableAudioRowEffectivelyMuted(state, row, subs));
	}

	@Test
	void rowEffectivelyMutedWhenAnotherPlayableRowSoloed() {
		TimelineTrackListState state = new TimelineTrackListState();
		List<TrackDefinition> subs = List.of(td("waveform"), td("stem_wf_drums"));
		int wfRow = TimelineTrackMeta.ROW_AUDIO_SUBS_START;
		int stemRow = wfRow + 1;
		state.setSoloed(stemRow, true);

		assertTrue(TimelineStemMuteSync.isPlayableAudioRowEffectivelyMuted(state, wfRow, subs));
		assertFalse(TimelineStemMuteSync.isPlayableAudioRowEffectivelyMuted(state, stemRow, subs));
	}
}
