package com.beatblock.timeline.interaction;

import com.beatblock.BeatBlockClient;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import java.util.ArrayList;
import java.util.List;

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
		if (timeline == null || selectionState == null) return;
		if (selectionState.getSelectedEvents().isEmpty() && selectionState.getSelectedClips().isEmpty()) {
			BeatBlockClient.LOGGER.warn("[TimelineInteraction.deleteSelectedEntries] No clips or events to delete");
			return;
		}

		List<String> clipIds = new ArrayList<>(selectionState.getSelectedClips());
		BeatBlockClient.LOGGER.info(String.format(
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
						BeatBlockClient.LOGGER.info(String.format(
							"[TimelineInteraction.deleteSelectedEntries] Removing clip %s from track %s",
							clipId, track.getId()));
						if (track.removeClip(clipId)) {
							BeatBlockClient.LOGGER.info(String.format(
								"[TimelineInteraction.deleteSelectedEntries] Clip removed successfully: %s", clipId));
							selectionState.deselectClip(clipId);
							timeline.markAnimationEventsDirty(track.getId());
							if (Timeline.TRACK_ID_AUDIO.equals(track.getId())) {
								onAudioRootClipDeleted(timeline, clipId);
							}
						} else {
							BeatBlockClient.LOGGER.warn(String.format(
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
			for (Clip clip : track.getClips()) {
				for (String eventId : eventIds) {
					if (eventId == null) continue;
					if (TimelineOperations.removeEvent(clip, eventId)) {
						selectionState.deselectEvent(eventId);
						timeline.markAnimationEventsDirty(track.getId());
					}
				}
			}
		}
	}

	public static void onAudioRootClipDeleted(Timeline timeline, String deletedClipId) {
		if (timeline == null || deletedClipId == null || deletedClipId.isBlank()) return;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null) return;

		if (!audioTrack.getClips().isEmpty()) return;

		if (audioTrack.getAudioData() != null) {
			audioTrack.getAudioData().setWaveform(null);
			audioTrack.getAudioData().clearAll();
			audioTrack.getAudioData().clearStemWaveforms();
		}

		timeline.setMetadata("audioRootClipId", null);
		timeline.setMetadata("audioAssetId", null);
		timeline.setMetadata("audioPath", null);
		timeline.setMetadata("awaitingAnalyzedBeatmap", null);
	}
}
