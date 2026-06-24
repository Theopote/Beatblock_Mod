package com.beatblock.timeline.rendering;

import com.beatblock.runtime.BeatBlockContext;

import java.util.List;

/** 时间线音频子轨静音/独奏 → StemMixer / MusicPlayer 同步。 */
public final class TimelineStemMuteSync {

	private TimelineStemMuteSync() {}

	public static void syncPrimaryPlayerMuteState(
		BeatBlockContext context,
		TimelineTrackListState trackListState,
		List<TrackDefinition> audioSubTracks
	) {
		if (context == null || context.musicPlayer() == null || trackListState == null) return;

		boolean anyAudioRowAudible = false;
		for (int slot = 0; slot < audioSubTracks.size(); slot++) {
			TrackDefinition td = audioSubTracks.get(slot);
			if (!"waveform".equals(td.getKey())) continue;
			int rowIndex = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (!isPlayableAudioRowEffectivelyMuted(trackListState, rowIndex, audioSubTracks)) {
				anyAudioRowAudible = true;
				break;
			}
		}

		context.musicPlayer().setMuted(!anyAudioRowAudible);
	}

	public static void syncStemMuteState(
		BeatBlockContext context,
		TimelineTrackListState trackListState,
		List<TrackDefinition> audioSubTracks
	) {
		if (context == null || context.stemMixer() == null || !context.stemMixer().hasStems()) return;
		syncOneStemMute(context, trackListState, audioSubTracks, "drums");
		syncOneStemMute(context, trackListState, audioSubTracks, "bass");
		syncOneStemMute(context, trackListState, audioSubTracks, "vocals");
		syncOneStemMute(context, trackListState, audioSubTracks, "other");
	}

	private static void syncOneStemMute(
		BeatBlockContext context,
		TimelineTrackListState trackListState,
		List<TrackDefinition> audioSubTracks,
		String stemName
	) {
		boolean hasMappedRow = false;
		boolean anyAudibleMappedRow = false;
		for (int slot = 0; slot < audioSubTracks.size(); slot++) {
			TrackDefinition td = audioSubTracks.get(slot);
			String key = td.getKey();
			if (!mapsToStemAudioControl(key, stemName)) continue;
			hasMappedRow = true;
			int rowIndex = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (!isPlayableAudioRowEffectivelyMuted(trackListState, rowIndex, audioSubTracks)) {
				anyAudibleMappedRow = true;
			}
		}

		boolean muted = hasMappedRow && !anyAudibleMappedRow;
		context.stemMixer().setStemMuted(stemName, muted);
	}

	public static boolean mapsToStemAudioControl(String trackKey, String stemName) {
		if (trackKey == null || stemName == null) return false;
		if (trackKey.startsWith("stem_wf_")) {
			return stemName.equals(trackKey.substring("stem_wf_".length()));
		}
		return false;
	}

	public static boolean isPlayableAudioControlRow(int rowIndex, List<TrackDefinition> audioSubTracks) {
		if (!TimelineTrackMeta.isAudioSubRow(rowIndex)) return false;
		int slot = TimelineTrackMeta.audioSubRowSlot(rowIndex);
		if (slot < 0 || slot >= audioSubTracks.size()) return false;
		String key = audioSubTracks.get(slot).getKey();
		return "waveform".equals(key) || (key != null && key.startsWith("stem_wf_"));
	}

	public static boolean isPlayableAudioRowEffectivelyMuted(
		TimelineTrackListState trackListState,
		int rowIndex,
		List<TrackDefinition> audioSubTracks
	) {
		if (trackListState == null) return false;
		if (trackListState.isMuted(rowIndex)) return true;

		boolean groupSoloed = trackListState.isSoloed(TimelineTrackMeta.ROW_AUDIO_GROUP);
		boolean anyPlayableSolo = groupSoloed;
		for (int slot = 0; slot < audioSubTracks.size(); slot++) {
			int candidateRowIndex = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (!isPlayableAudioControlRow(candidateRowIndex, audioSubTracks)) continue;
			if (trackListState.isSoloed(candidateRowIndex)) {
				anyPlayableSolo = true;
				break;
			}
		}

		if (!anyPlayableSolo) return false;
		if (groupSoloed) return false;
		return !trackListState.isSoloed(rowIndex);
	}
}
