package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard.CreatedClipRef;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard.PasteResult;
import com.beatblock.timeline.interaction.TimelineInteractionClipboard.PastedEventRef;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 从时间线剪贴板粘贴事件，支持撤销/重做。 */
public final class PasteTimelineEventsCommand implements Command {

	private final TimelineInteractionClipboard.PasteRequest request;
	private PasteResult snapshot = PasteResult.empty();
	private boolean applied;

	public PasteTimelineEventsCommand(TimelineInteractionClipboard.PasteRequest request) {
		this.request = request;
	}

	@Override
	public void execute() {
		if (applied) {
			return;
		}
		if (!snapshot.isEmpty()) {
			reapply(snapshot);
			applied = true;
			return;
		}
		snapshot = TimelineInteractionClipboard.pasteUndoable(request);
		applied = !snapshot.isEmpty();
	}

	@Override
	public void undo() {
		if (!applied || snapshot.isEmpty()) {
			return;
		}
		removeApplied(snapshot);
		applied = false;
	}

	private void reapply(PasteResult result) {
		Timeline timeline = request.timeline();
		if (timeline == null) {
			return;
		}
		Set<String> dirtyTracks = new HashSet<>();
		Map<String, Clip> clipsByTrack = new HashMap<>();
		for (CreatedClipRef created : result.createdClips()) {
			Track track = timeline.getTrack(created.trackId());
			if (track == null) {
				continue;
			}
			Clip clip = track.getClip(created.clipId());
			if (clip == null) {
				double start = Math.max(0, request.anchorTimeSeconds() - 0.05);
				clip = TimelineOperations.addClip(track, start, start + 0.2);
			}
			if (clip != null) {
				clipsByTrack.put(created.trackId(), clip);
			}
		}
		for (PastedEventRef pasted : result.pastedEvents()) {
			Track track = timeline.getTrack(pasted.trackId());
			if (track == null) {
				continue;
			}
			Clip clip = clipsByTrack.get(pasted.trackId());
			if (clip == null) {
				clip = track.getClip(pasted.clipId());
			}
			if (clip == null) {
				continue;
			}
			clip.addEvent(cloneEvent(pasted.event()));
			dirtyTracks.add(track.getId());
			if (request.selectionState() != null) {
				request.selectionState().selectEvent(pasted.event().getId());
			}
		}
		for (String trackId : dirtyTracks) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	private void removeApplied(PasteResult result) {
		Timeline timeline = request.timeline();
		if (timeline == null) {
			return;
		}
		Set<String> dirtyTracks = new HashSet<>();
		for (int i = result.pastedEvents().size() - 1; i >= 0; i--) {
			PastedEventRef pasted = result.pastedEvents().get(i);
			Track track = timeline.getTrack(pasted.trackId());
			if (track == null) {
				continue;
			}
			Clip clip = track.getClip(pasted.clipId());
			if (clip != null && TimelineOperations.removeEvent(clip, pasted.event().getId())) {
				dirtyTracks.add(track.getId());
			}
		}
		for (CreatedClipRef created : result.createdClips()) {
			Track track = timeline.getTrack(created.trackId());
			if (track == null) {
				continue;
			}
			Clip clip = track.getClip(created.clipId());
			if (clip != null && clip.getEvents().isEmpty()) {
				track.removeClip(created.clipId());
				dirtyTracks.add(track.getId());
			}
		}
		for (String trackId : dirtyTracks) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	private static TimelineEvent cloneEvent(TimelineEvent source) {
		return new TimelineEvent(
			source.getId(),
			source.getTimeSeconds(),
			source.getType(),
			new java.util.HashMap<>(source.getParameters())
		);
	}
}
