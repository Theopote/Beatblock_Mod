package com.beatblock.timeline.interaction;

import com.beatblock.BeatBlock;
import com.beatblock.BeatBlockClient;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerBindingSupport;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.AudioTrackData;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.WaveformData;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 删除选中项与上下文片段删除判定。 */
public final class TimelineInteractionDeleteSupport {

	private TimelineInteractionDeleteSupport() {}

	public static boolean hasDeletableSelection(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState
	) {
		if (timeline == null || selectionState == null) return false;

		if (!selectionState.getSelectedClips().isEmpty()) {
			for (String clipId : selectionState.getSelectedClips()) {
				if (clipId == null) continue;
				for (Track track : timeline.getTracks()) {
					if (track.getClip(clipId) != null
						&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, track.getId())) {
						return true;
					}
				}
			}
		}

		if (selectionState.getSelectedEvents().isEmpty()) return false;
		for (String eventId : selectionState.getSelectedEvents()) {
			TimelineEventRef ref = TimelineEventRefs.find(timeline, eventId);
			if (ref != null && !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, ref.track().getId())) {
				return true;
			}
		}
		return false;
	}

	public static boolean canDeleteContextClip(
		Timeline timeline,
		TimelineTrackListState trackListState,
		String contextTrackId,
		String contextClipId
	) {
		if (timeline == null || contextClipId == null) {
			BeatBlockClient.LOGGER.debug(String.format(
				"[TimelineInteraction.canDeleteContextClip] Early return: timeline=%s, contextClipId=%s",
				timeline != null, contextClipId
			));
			return false;
		}
		if (contextTrackId != null && !contextTrackId.isBlank()) {
			Track track = timeline.getTrack(contextTrackId);
			boolean trackExists = track != null;
			boolean clipExists = trackExists && track.getClip(contextClipId) != null;
			boolean trackNotLocked = !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, contextTrackId);
			boolean result = trackExists && clipExists && trackNotLocked;
			BeatBlockClient.LOGGER.debug(String.format(
				"[TimelineInteraction.canDeleteContextClip] With contextTrackId: trackExists=%s, clipExists=%s, trackNotLocked=%s, result=%s",
				trackExists, clipExists, trackNotLocked, result
			));
			return result;
		}
		for (Track track : timeline.getTracks()) {
			Clip clip = track.getClip(contextClipId);
			if (clip != null) {
				boolean trackNotLocked = !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, track.getId());
				BeatBlockClient.LOGGER.debug(String.format(
					"[TimelineInteraction.canDeleteContextClip] Found clip in track %s: trackNotLocked=%s",
					track.getId(), trackNotLocked
				));
				return trackNotLocked;
			}
		}
		BeatBlockClient.LOGGER.debug("[TimelineInteraction.canDeleteContextClip] Clip not found in any track");
		return false;
	}

	public static void deleteSelectedEntries(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState
	) {
		deleteSelectedEntries(timeline, selectionState, trackListState, currentLayerManager());
	}

	static void deleteSelectedEntries(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		BuildLayerManager layerManager
	) {
		if (timeline == null || selectionState == null) return;
		if (selectionState.getSelectedEvents().isEmpty() && selectionState.getSelectedClips().isEmpty()) {
			BeatBlockClient.LOGGER.debug("[TimelineInteraction.deleteSelectedEntries] No clips or events to delete");
			return;
		}

		List<String> clipIds = new ArrayList<>(selectionState.getSelectedClips());
		BeatBlockClient.LOGGER.debug(String.format(
			"[TimelineInteraction.deleteSelectedEntries] Starting: clipIds=%s, eventIds=%s",
			clipIds, selectionState.getSelectedEvents()
		));
		if (!clipIds.isEmpty()) {
			for (Track track : timeline.getTracks()) {
				if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, track.getId())) {
					BeatBlockClient.LOGGER.debug(String.format("[TimelineInteraction.deleteSelectedEntries] Track locked: %s", track.getId()));
					continue;
				}
				for (String clipId : clipIds) {
					if (clipId == null) continue;
					Clip clip = track.getClip(clipId);
					if (clip != null) {
						BeatBlockClient.LOGGER.debug(String.format(
							"[TimelineInteraction.deleteSelectedEntries] Removing clip %s from track %s",
							clipId, track.getId()));
						if (track.removeClip(clipId)) {
							unbindLayerForClip(layerManager, clipId);
							BeatBlockClient.LOGGER.debug(String.format(
								"[TimelineInteraction.deleteSelectedEntries] Clip removed successfully: %s", clipId));
							selectionState.deselectClip(clipId);
							timeline.markAnimationEventsDirty(track.getId());
							if (Timeline.TRACK_ID_AUDIO.equals(track.getId())) {
								onAudioRootClipDeleted(timeline, clipId);
							}
						} else {
							BeatBlockClient.LOGGER.debug(String.format(
								"[TimelineInteraction.deleteSelectedEntries] Failed to remove clip: %s", clipId));
						}
					} else {
						BeatBlockClient.LOGGER.debug(String.format(
							"[TimelineInteraction.deleteSelectedEntries] Clip not found in track %s: %s",
							track.getId(), clipId));
					}
				}
			}
		}

		List<String> eventIds = new ArrayList<>(selectionState.getSelectedEvents());
		for (Track track : timeline.getTracks()) {
			if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, track.getId())) continue;
			for (Clip clip : new ArrayList<>(track.getClips())) {
				for (String eventId : eventIds) {
					if (eventId == null) continue;
					var event = clip.getEvent(eventId);
					BuildLayer boundLayer = layerManager != null ? layerManager.getByClipId(clip.getId()) : null;
					boolean removesLayerBinding = BuildLayerBindingSupport.isLayerBindingEvent(event, boundLayer);
					if (TimelineOperations.removeEvent(clip, eventId)) {
						if (removesLayerBinding) {
							layerManager.unbindFromClip(boundLayer);
						}
						selectionState.deselectEvent(eventId);
						timeline.markAnimationEventsDirty(track.getId());
						if (clip.getEvents().isEmpty() && removesLayerBinding) {
							if (track.removeClip(clip.getId())) {
								selectionState.deselectClip(clip.getId());
							}
						}
					}
				}
			}
		}
	}

	private static void unbindLayerForClip(BuildLayerManager manager, String clipId) {
		if (manager == null) return;
		BuildLayer layer = manager.getByClipId(clipId);
		if (layer != null) manager.unbindFromClip(layer);
	}

	private static BuildLayerManager currentLayerManager() {
		try {
			return BeatBlock.getContext().buildLayerManager();
		} catch (IllegalStateException ignored) {
			return null;
		}
	}

	public static void onAudioRootClipDeleted(Timeline timeline, String deletedClipId) {
		cleanupAudioRootIfEmpty(timeline, deletedClipId);
	}

	/**
	 * If the audio track has no clips left after {@code deletedClipId} was removed,
	 * capture restorables then clear waveform / feature / root metadata.
	 *
	 * @return snapshot to restore on Undo, or {@code null} when no cleanup ran
	 */
	public static @Nullable AudioRootCleanupSnapshot cleanupAudioRootIfEmpty(
		Timeline timeline,
		String deletedClipId
	) {
		if (timeline == null || deletedClipId == null || deletedClipId.isBlank()) return null;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null) return null;
		if (!audioTrack.getClips().isEmpty()) return null;

		AudioRootCleanupSnapshot snapshot = captureAudioRootState(timeline);
		clearAudioRootState(timeline, audioTrack);
		return snapshot;
	}

	public static void restoreAudioRootState(Timeline timeline, @Nullable AudioRootCleanupSnapshot snapshot) {
		if (timeline == null || snapshot == null) return;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null) return;

		timeline.setMetadata("audioRootClipId", snapshot.audioRootClipId());
		timeline.setMetadata("audioAssetId", snapshot.audioAssetId());
		timeline.setMetadata("audioPath", snapshot.audioPath());
		timeline.setMetadata("awaitingAnalyzedBeatmap", snapshot.awaitingAnalyzedBeatmap());

		AudioTrackData data = audioTrack.getAudioData();
		if (data == null) {
			data = new AudioTrackData();
			audioTrack.setAudioData(data);
		}
		data.clearAll();
		data.setWaveform(snapshot.waveform());
		for (Map.Entry<String, List<FeatureEvent>> entry : snapshot.featureTracks().entrySet()) {
			String key = entry.getKey();
			String label = snapshot.featureLabels().getOrDefault(key, key);
			for (FeatureEvent event : entry.getValue()) {
				data.addFeatureEvent(key, label, event);
			}
		}
		for (Map.Entry<String, WaveformData> entry : snapshot.stemWaveforms().entrySet()) {
			data.setStemWaveform(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * When the deleted clip was {@code audioRootClipId} but other audio clips remain,
	 * promote another clip to root so metadata does not dangle.
	 *
	 * @return reassignment snapshot for Undo, or {@code null} when no reassignment happened
	 */
	public static @Nullable AudioRootReassignSnapshot reassignAudioRootIfNeeded(
		Timeline timeline,
		String deletedClipId
	) {
		if (timeline == null || deletedClipId == null || deletedClipId.isBlank()) return null;
		Object currentRoot = timeline.getMetadata("audioRootClipId");
		if (currentRoot == null || !deletedClipId.equals(String.valueOf(currentRoot))) {
			return null;
		}
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null || audioTrack.getClips().isEmpty()) {
			return null;
		}
		Clip promote = audioTrack.getClips().getFirst();
		if (promote == null) return null;
		timeline.setMetadata("audioRootClipId", promote.getId());
		return new AudioRootReassignSnapshot(deletedClipId, promote.getId());
	}

	public static void restoreAudioRootReassign(
		Timeline timeline,
		@Nullable AudioRootReassignSnapshot snapshot
	) {
		if (timeline == null || snapshot == null) return;
		timeline.setMetadata("audioRootClipId", snapshot.previousRootClipId());
	}

	/** Per-clip audio metadata keys cleared when an audio clip is deleted. */
	public static Map<String, Object> captureAndClearClipAudioMetadata(Timeline timeline, String clipId) {
		if (timeline == null || clipId == null || clipId.isBlank()) return Map.of();
		Map<String, Object> captured = new LinkedHashMap<>();
		for (String prefix : List.of("clipLabel_", "clipAudioPath_", "clipAudioKey_")) {
			String key = prefix + clipId;
			Object value = timeline.getMetadata(key);
			if (value != null) {
				captured.put(key, value);
				timeline.setMetadata(key, null);
			}
		}
		return Map.copyOf(captured);
	}

	public static void restoreClipAudioMetadata(Timeline timeline, @Nullable Map<String, Object> metadata) {
		if (timeline == null || metadata == null || metadata.isEmpty()) return;
		for (Map.Entry<String, Object> entry : metadata.entrySet()) {
			timeline.setMetadata(entry.getKey(), entry.getValue());
		}
	}

	public record AudioRootReassignSnapshot(
		@NonNull String previousRootClipId,
		@NonNull String newRootClipId
	) {}

	public record AudioRootCleanupSnapshot(
		@Nullable Object audioRootClipId,
		@Nullable Object audioAssetId,
		@Nullable Object audioPath,
		@Nullable Object awaitingAnalyzedBeatmap,
		@Nullable WaveformData waveform,
		Map<String, List<FeatureEvent>> featureTracks,
		Map<String, String> featureLabels,
		Map<String, WaveformData> stemWaveforms
	) {
		public AudioRootCleanupSnapshot {
			featureTracks = copyFeatureTracks(featureTracks);
			featureLabels = featureLabels != null ? Map.copyOf(featureLabels) : Map.of();
			stemWaveforms = stemWaveforms != null ? Map.copyOf(stemWaveforms) : Map.of();
		}
	}

	private static AudioRootCleanupSnapshot captureAudioRootState(Timeline timeline) {
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		AudioTrackData data = audioTrack != null ? audioTrack.getAudioData() : null;
		Map<String, List<FeatureEvent>> features = new LinkedHashMap<>();
		Map<String, String> labels = new LinkedHashMap<>();
		Map<String, WaveformData> stems = new LinkedHashMap<>();
		WaveformData waveform = null;
		if (data != null) {
			waveform = data.getWaveform();
			for (Map.Entry<String, FeatureTrack> entry : data.getFeatureTracks().entrySet()) {
				FeatureTrack track = entry.getValue();
				labels.put(entry.getKey(), track.getLabel());
				List<FeatureEvent> events = new ArrayList<>(track.getEvents().size());
				for (FeatureEvent event : track.getEvents()) {
					events.add(new FeatureEvent(event.getTimeSeconds(), event.getEnergy()));
				}
				features.put(entry.getKey(), List.copyOf(events));
			}
			for (String stemKey : data.getStemWaveformKeys()) {
				WaveformData stem = data.getStemWaveform(stemKey);
				if (stem != null) {
					stems.put(stemKey, stem);
				}
			}
		}
		return new AudioRootCleanupSnapshot(
			timeline.getMetadata("audioRootClipId"),
			timeline.getMetadata("audioAssetId"),
			timeline.getMetadata("audioPath"),
			timeline.getMetadata("awaitingAnalyzedBeatmap"),
			waveform,
			features,
			labels,
			stems
		);
	}

	private static void clearAudioRootState(Timeline timeline, Track audioTrack) {
		var audioData = audioTrack.getAudioData();
		if (audioData != null) {
			audioData.setWaveform(null);
			audioData.clearAll();
			audioData.clearStemWaveforms();
		}
		timeline.setMetadata("audioRootClipId", null);
		timeline.setMetadata("audioAssetId", null);
		timeline.setMetadata("audioPath", null);
		timeline.setMetadata("awaitingAnalyzedBeatmap", null);
	}

	private static Map<String, List<FeatureEvent>> copyFeatureTracks(
		Map<String, List<FeatureEvent>> source
	) {
		if (source == null || source.isEmpty()) return Map.of();
		Map<String, List<FeatureEvent>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, List<FeatureEvent>> entry : source.entrySet()) {
			List<FeatureEvent> events = new ArrayList<>();
			if (entry.getValue() != null) {
				for (FeatureEvent event : entry.getValue()) {
					events.add(new FeatureEvent(event.getTimeSeconds(), event.getEnergy()));
				}
			}
			copy.put(entry.getKey(), List.copyOf(events));
		}
		return Map.copyOf(copy);
	}
}
