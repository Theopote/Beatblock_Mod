package com.beatblock.timeline.command;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerBindingSupport;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.interaction.TimelineInteractiveTrackSlots;
import com.beatblock.timeline.interaction.TimelineInteractionDeleteSupport;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Undoable delete of selected timeline clips and/or events.
 * <p>
 * Unbinds BuildLayers when a binding clip/event is removed, and drops empty
 * binding clips after event-only deletes so layers are not left orphaned.
 * <p>
 * First execute captures a deletion snapshot from the current selection; undo
 * restores document state but keeps the snapshot so redo does not depend on
 * selection still being intact.
 */
public final class DeleteSelectedTimelineEntriesCommand implements Command {

	private record RemovedClip(
		@NonNull String trackId,
		@NonNull Clip snapshot,
		BuildLayerBindingSupport.@Nullable BindingSnapshot binding,
		Map<String, Object> clipAudioMetadata
	) {
		private RemovedClip {
			clipAudioMetadata = clipAudioMetadata != null ? Map.copyOf(clipAudioMetadata) : Map.of();
		}
	}

	private record RemovedEvent(
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull TimelineEvent snapshot,
		BuildLayerBindingSupport.@Nullable BindingSnapshot binding,
		boolean removedEmptyClip,
		@Nullable Clip emptyClipSnapshot
	) {}

	private final Timeline timeline;
	private final @Nullable BuildLayerManager layerManager;
	private final SelectionState selectionState;
	private final TimelineTrackListState trackListState;

	private final List<RemovedClip> removedClips = new ArrayList<>();
	private final List<RemovedEvent> removedEvents = new ArrayList<>();
	private final List<String> audioRootCleanupClipIds = new ArrayList<>();
	private TimelineInteractionDeleteSupport.@Nullable AudioRootCleanupSnapshot audioRootSnapshot;
	private TimelineInteractionDeleteSupport.@Nullable AudioRootReassignSnapshot audioRootReassign;
	private boolean executed;
	private boolean snapshotCaptured;

	public boolean wasApplied() {
		return executed;
	}

	public DeleteSelectedTimelineEntriesCommand(
		@NonNull Timeline timeline,
		@NonNull SelectionState selectionState,
		@NonNull TimelineTrackListState trackListState
	) {
		this(timeline, currentLayerManager(), selectionState, trackListState);
	}

	public DeleteSelectedTimelineEntriesCommand(
		@NonNull Timeline timeline,
		@Nullable BuildLayerManager layerManager,
		@NonNull SelectionState selectionState,
		@NonNull TimelineTrackListState trackListState
	) {
		this.timeline = timeline;
		this.layerManager = layerManager;
		this.selectionState = selectionState;
		this.trackListState = trackListState;
	}

	@Override
	public void execute() {
		if (executed || timeline == null || selectionState == null) {
			return;
		}
		if (snapshotCaptured) {
			reapplyFromSnapshots();
			executed = true;
			return;
		}

		removedClips.clear();
		removedEvents.clear();
		audioRootCleanupClipIds.clear();
		audioRootSnapshot = null;
		audioRootReassign = null;

		Set<String> clipIds = new LinkedHashSet<>(selectionState.getSelectedClips());
		Set<String> eventIds = new LinkedHashSet<>(selectionState.getSelectedEvents());

		if (!clipIds.isEmpty()) {
			for (Track track : timeline.getTracks()) {
				if (track == null || TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, track.getId())) {
					continue;
				}
				for (String clipId : clipIds) {
					if (clipId == null) continue;
					Clip clip = track.getClip(clipId);
					if (clip == null) continue;
					Clip snapshot = copyClip(clip);
					BuildLayerBindingSupport.BindingSnapshot binding = captureBindingForClip(clipId);
					Map<String, Object> clipMeta =
						TimelineInteractionDeleteSupport.captureAndClearClipAudioMetadata(timeline, clipId);
					if (!removeClipNow(track, clipId, snapshot, binding)) {
						TimelineInteractionDeleteSupport.restoreClipAudioMetadata(timeline, clipMeta);
						continue;
					}
					removedClips.add(new RemovedClip(track.getId(), snapshot, binding, clipMeta));
				}
			}
		}

		if (!eventIds.isEmpty()) {
			for (Track track : timeline.getTracks()) {
				if (track == null || TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, track.getId())) {
					continue;
				}
				List<Clip> clips = new ArrayList<>(track.getClips());
				for (Clip clip : clips) {
					if (clip == null) continue;
					for (String eventId : eventIds) {
						if (eventId == null) continue;
						TimelineEvent event = clip.getEvent(eventId);
						if (event == null) continue;
						RemovedEvent removed = removeEventNow(track, clip, event);
						if (removed != null) {
							removedEvents.add(removed);
						}
					}
				}
			}
		}

		snapshotCaptured = !removedClips.isEmpty() || !removedEvents.isEmpty();
		executed = snapshotCaptured;
	}

	@Override
	public void undo() {
		if (!executed) {
			return;
		}
		for (int i = removedEvents.size() - 1; i >= 0; i--) {
			RemovedEvent removed = removedEvents.get(i);
			Clip emptySnap = removed.emptyClipSnapshot();
			if (!removed.removedEmptyClip() || emptySnap == null) continue;
			Track track = timeline.getTrack(removed.trackId());
			if (track == null) continue;
			if (track.getClip(removed.clipId()) == null) {
				track.addClip(copyClip(emptySnap));
			}
		}
		for (int i = removedClips.size() - 1; i >= 0; i--) {
			RemovedClip removed = removedClips.get(i);
			Track track = timeline.getTrack(removed.trackId());
			if (track == null) continue;
			if (track.getClip(removed.snapshot().getId()) == null) {
				track.addClip(copyClip(removed.snapshot()));
			}
			BuildLayerBindingSupport.restoreBinding(layerManager, removed.binding());
			TimelineInteractionDeleteSupport.restoreClipAudioMetadata(timeline, removed.clipAudioMetadata());
			timeline.markAnimationEventsDirty(track.getId());
		}
		for (int i = removedEvents.size() - 1; i >= 0; i--) {
			RemovedEvent removed = removedEvents.get(i);
			Track track = timeline.getTrack(removed.trackId());
			if (track == null) continue;
			Clip clip = track.getClip(removed.clipId());
			if (clip == null) continue;
			if (clip.getEvent(removed.snapshot().getId()) == null) {
				clip.addEvent(copyEvent(removed.snapshot()));
			}
			BuildLayerBindingSupport.restoreBinding(layerManager, removed.binding());
			timeline.markAnimationEventsDirty(track.getId());
		}
		if (audioRootReassign != null) {
			TimelineInteractionDeleteSupport.restoreAudioRootReassign(timeline, audioRootReassign);
		}
		if (audioRootSnapshot != null) {
			TimelineInteractionDeleteSupport.restoreAudioRootState(timeline, audioRootSnapshot);
		}
		// Keep snapshots so redo can reapply without relying on selection.
		executed = false;
	}

	private void reapplyFromSnapshots() {
		for (RemovedClip removed : removedClips) {
			Track track = timeline.getTrack(removed.trackId());
			if (track == null) continue;
			Clip clip = track.getClip(removed.snapshot().getId());
			if (clip == null) continue;
			TimelineInteractionDeleteSupport.captureAndClearClipAudioMetadata(
				timeline, removed.snapshot().getId());
			removeClipNow(track, removed.snapshot().getId(), removed.snapshot(), removed.binding());
		}
		for (RemovedEvent removed : removedEvents) {
			Track track = timeline.getTrack(removed.trackId());
			if (track == null) continue;
			Clip clip = track.getClip(removed.clipId());
			if (clip == null) {
				if (removed.removedEmptyClip() && removed.emptyClipSnapshot() != null) {
					clip = copyClip(removed.emptyClipSnapshot());
					track.addClip(clip);
				} else {
					continue;
				}
			}
			TimelineEvent event = clip.getEvent(removed.snapshot().getId());
			if (event == null) {
				event = copyEvent(removed.snapshot());
				clip.addEvent(event);
			}
			removeEventNow(track, clip, event);
		}
	}

	private boolean removeClipNow(
		Track track,
		String clipId,
		Clip snapshot,
		BuildLayerBindingSupport.@Nullable BindingSnapshot binding
	) {
		if (!track.removeClip(clipId)) return false;
		if (binding != null && layerManager != null) {
			BuildLayer layer = layerManager.get(binding.layerId());
			if (layer != null) {
				layerManager.unbindFromClip(layer);
			}
		}
		for (TimelineEvent event : snapshot.getEvents()) {
			selectionState.deselectEvent(event.getId());
		}
		if (selectionState.getRangeAnchorEventId() != null
			&& snapshot.getEvent(selectionState.getRangeAnchorEventId()) != null) {
			selectionState.setRangeAnchorEventId(null);
		}
		selectionState.deselectClip(clipId);
		timeline.markAnimationEventsDirty(track.getId());
		if (Timeline.TRACK_ID_AUDIO.equals(track.getId())) {
			if (!audioRootCleanupClipIds.contains(clipId)) {
				audioRootCleanupClipIds.add(clipId);
			}
			var cleanup = TimelineInteractionDeleteSupport.cleanupAudioRootIfEmpty(timeline, clipId);
			if (cleanup != null) {
				if (audioRootSnapshot == null) {
					audioRootSnapshot = cleanup;
				}
			} else {
				var reassign = TimelineInteractionDeleteSupport.reassignAudioRootIfNeeded(timeline, clipId);
				if (reassign != null && audioRootReassign == null) {
					audioRootReassign = reassign;
				}
			}
		}
		return true;
	}

	private @Nullable RemovedEvent removeEventNow(Track track, Clip clip, TimelineEvent event) {
		TimelineEvent eventSnapshot = copyEvent(event);
		BuildLayerBindingSupport.BindingSnapshot binding =
			BuildLayerBindingSupport.unbindIfBindingEvent(layerManager, clip.getId(), event);
		if (!clip.removeEvent(event.getId())) {
			if (binding != null) {
				BuildLayerBindingSupport.restoreBinding(layerManager, binding);
			}
			return null;
		}
		selectionState.deselectEvent(event.getId());
		if (event.getId().equals(selectionState.getRangeAnchorEventId())) {
			selectionState.setRangeAnchorEventId(null);
		}
		timeline.markAnimationEventsDirty(track.getId());

		boolean removedEmpty = false;
		Clip emptySnap = null;
		if (clip.getEvents().isEmpty() && binding != null) {
			emptySnap = copyClip(clip);
			if (track.removeClip(clip.getId())) {
				removedEmpty = true;
				selectionState.deselectClip(clip.getId());
			} else {
				emptySnap = null;
			}
		}
		return new RemovedEvent(
			track.getId(),
			clip.getId(),
			eventSnapshot,
			binding,
			removedEmpty,
			emptySnap
		);
	}

	private BuildLayerBindingSupport.@Nullable BindingSnapshot captureBindingForClip(String clipId) {
		if (layerManager == null) return null;
		BuildLayer layer = layerManager.getByClipId(clipId);
		if (layer == null) return null;
		return new BuildLayerBindingSupport.BindingSnapshot(
			layer.getId(),
			layer.getBoundClipId(),
			layer.getState()
		);
	}

	private static Clip copyClip(Clip source) {
		Clip copy = new Clip(source.getId(), source.getStartTimeSeconds(), source.getEndTimeSeconds());
		for (TimelineEvent event : source.getEvents()) {
			copy.addEvent(copyEvent(event));
		}
		return copy;
	}

	private static TimelineEvent copyEvent(TimelineEvent source) {
		Map<String, Object> params = new HashMap<>(source.getParameters());
		return new TimelineEvent(source.getId(), source.getTimeSeconds(), source.getType(), params);
	}

	private static @Nullable BuildLayerManager currentLayerManager() {
		try {
			return BeatBlock.getContext().buildLayerManager();
		} catch (IllegalStateException ignored) {
			return null;
		}
	}
}
