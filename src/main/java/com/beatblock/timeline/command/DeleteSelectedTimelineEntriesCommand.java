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
 */
public final class DeleteSelectedTimelineEntriesCommand implements Command {

	private record RemovedClip(
		@NonNull String trackId,
		@NonNull Clip snapshot,
		BuildLayerBindingSupport.@Nullable BindingSnapshot binding
	) {}

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
	private boolean executed;

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
		removedClips.clear();
		removedEvents.clear();
		audioRootCleanupClipIds.clear();

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
					if (!track.removeClip(clipId)) continue;
					if (binding != null && layerManager != null) {
						BuildLayer layer = layerManager.get(binding.layerId());
						if (layer != null) {
							layerManager.unbindFromClip(layer);
						}
					}
					removedClips.add(new RemovedClip(track.getId(), snapshot, binding));
					selectionState.deselectClip(clipId);
					timeline.markAnimationEventsDirty(track.getId());
					if (Timeline.TRACK_ID_AUDIO.equals(track.getId())) {
						audioRootCleanupClipIds.add(clipId);
						TimelineInteractionDeleteSupport.onAudioRootClipDeleted(timeline, clipId);
					}
				}
			}
		}

		if (!eventIds.isEmpty()) {
			for (Track track : timeline.getTracks()) {
				if (track == null || TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, track.getId())) {
					continue;
				}
				// Snapshot clip list — may mutate while iterating
				List<Clip> clips = new ArrayList<>(track.getClips());
				for (Clip clip : clips) {
					if (clip == null) continue;
					for (String eventId : eventIds) {
						if (eventId == null) continue;
						TimelineEvent event = clip.getEvent(eventId);
						if (event == null) continue;
						TimelineEvent eventSnapshot = copyEvent(event);
						BuildLayerBindingSupport.BindingSnapshot binding =
							BuildLayerBindingSupport.unbindIfBindingEvent(layerManager, clip.getId(), event);
						if (!clip.removeEvent(eventId)) {
							if (binding != null) {
								BuildLayerBindingSupport.restoreBinding(layerManager, binding);
							}
							continue;
						}
						selectionState.deselectEvent(eventId);
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
						removedEvents.add(new RemovedEvent(
							track.getId(),
							clip.getId(),
							eventSnapshot,
							binding,
							removedEmpty,
							emptySnap
						));
					}
				}
			}
		}

		executed = !removedClips.isEmpty() || !removedEvents.isEmpty();
	}

	@Override
	public void undo() {
		if (!executed) {
			return;
		}
		// Restore clips first (empty binding shells + full clip deletes)
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
		removedClips.clear();
		removedEvents.clear();
		audioRootCleanupClipIds.clear();
		executed = false;
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
