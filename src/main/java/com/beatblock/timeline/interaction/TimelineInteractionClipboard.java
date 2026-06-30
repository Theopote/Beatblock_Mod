package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 时间线事件剪贴板复制/粘贴。 */
public final class TimelineInteractionClipboard {

	private TimelineInteractionClipboard() {}

	public record ClipboardEvent(
		String trackId,
		String clipId,
		double timeSeconds,
		EventType type,
		Map<String, Object> parameters
	) {}

	public record PasteRequest(
		Timeline timeline,
		SelectionState selectionState,
		List<ClipboardEvent> clipboard,
		double anchorTimeSeconds,
		String contextTrackId,
		String contextClipId,
		TimelineTrackListState trackListState
	) {}

	public static void copy(List<ClipboardEvent> target, Timeline timeline, SelectionState selectionState) {
		target.clear();
		if (timeline == null || selectionState == null) return;
		if (selectionState.getSelectedEvents().isEmpty()) return;
		Set<String> selected = new HashSet<>(selectionState.getSelectedEvents());
		for (Track track : timeline.getTracks()) {
			for (Clip clip : track.getClips()) {
				for (TimelineEvent e : clip.getEvents()) {
					if (!selected.contains(e.getId())) continue;
					target.add(new ClipboardEvent(
						track.getId(),
						clip.getId(),
						e.getTimeSeconds(),
						e.getType(),
						new HashMap<>(e.getParameters())
					));
				}
			}
		}
		target.sort(Comparator.comparingDouble(a -> a.timeSeconds));
	}

	public static void paste(PasteRequest request) {
		pasteUndoable(request);
	}

	/** 粘贴并返回可撤销快照；无有效粘贴时返回 {@link PasteResult#empty()}。 */
	public static PasteResult pasteUndoable(PasteRequest request) {
		if (request == null || request.timeline() == null || request.selectionState() == null) {
			return PasteResult.empty();
		}
		List<ClipboardEvent> clipboard = request.clipboard();
		if (clipboard == null || clipboard.isEmpty()) {
			return PasteResult.empty();
		}

		Timeline timeline = request.timeline();
		SelectionState selectionState = request.selectionState();
		double anchorTimeSeconds = request.anchorTimeSeconds();
		TimelineTrackListState trackListState = request.trackListState();

		double baseTime = clipboard.getFirst().timeSeconds;
		double maxTime = clipboard.getLast().timeSeconds;
		double span = Math.max(0.2, maxTime - baseTime);
		selectionState.clearEvents();
		Set<String> dirtyTracks = new HashSet<>();
		Map<String, Clip> targetClipsByTrack = new HashMap<>();
		List<PastedEventRef> pasted = new ArrayList<>();
		List<CreatedClipRef> createdClips = new ArrayList<>();

		for (ClipboardEvent src : clipboard) {
			double newTime = Math.max(0, anchorTimeSeconds + (src.timeSeconds - baseTime));
			Track targetTrack = resolvePasteTargetTrack(
				timeline, src, trackListState, request.contextTrackId());
			if (targetTrack == null) continue;
			Clip targetClip = resolveOrCreatePasteTargetClip(
				timeline,
				targetTrack,
				newTime,
				anchorTimeSeconds,
				span,
				targetClipsByTrack,
				request.contextTrackId(),
				request.contextClipId(),
				createdClips);
			if (targetClip == null) continue;
			TimelineEvent added = TimelineOperations.addEvent(targetClip, newTime, src.type, new HashMap<>(src.parameters));
			if (added != null) {
				selectionState.selectEvent(added.getId());
				dirtyTracks.add(targetTrack.getId());
				pasted.add(new PastedEventRef(targetTrack.getId(), targetClip.getId(), cloneEvent(added)));
			}
		}

		for (String trackId : dirtyTracks) {
			timeline.markAnimationEventsDirty(trackId);
		}
		return new PasteResult(pasted, createdClips);
	}

	public record PastedEventRef(String trackId, String clipId, TimelineEvent event) {}

	public record CreatedClipRef(String trackId, String clipId) {}

	public record PasteResult(List<PastedEventRef> pastedEvents, List<CreatedClipRef> createdClips) {
		public static PasteResult empty() {
			return new PasteResult(List.of(), List.of());
		}

		public boolean isEmpty() {
			return pastedEvents.isEmpty();
		}
	}

	private static TimelineEvent cloneEvent(TimelineEvent source) {
		return new TimelineEvent(
			source.getId(),
			source.getTimeSeconds(),
			source.getType(),
			new HashMap<>(source.getParameters())
		);
	}

	private static Track resolvePasteTargetTrack(
		Timeline timeline,
		ClipboardEvent src,
		TimelineTrackListState trackListState,
		String contextTrackId
	) {
		if (contextTrackId != null) {
			Track t = timeline.getTrack(contextTrackId);
			if (t != null && !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, t.getId())) return t;
		}
		Track fallback = timeline.getTrack(src.trackId);
		if (fallback == null) return null;
		return TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, fallback.getId()) ? null : fallback;
	}

	private static Clip resolveOrCreatePasteTargetClip(
		Timeline timeline,
		Track targetTrack,
		double eventTime,
		double anchorTime,
		double span,
		Map<String, Clip> targetClipsByTrack,
		String contextTrackId,
		String contextClipId,
		List<CreatedClipRef> createdClips
	) {
		Clip cached = targetClipsByTrack.get(targetTrack.getId());
		if (cached != null) return cached;

		if (contextTrackId != null && contextTrackId.equals(targetTrack.getId()) && contextClipId != null) {
			Clip contextClip = targetTrack.getClip(contextClipId);
			if (contextClip != null) {
				targetClipsByTrack.put(targetTrack.getId(), contextClip);
				return contextClip;
			}
		}

		for (Clip clip : targetTrack.getClips()) {
			if (eventTime >= clip.getStartTimeSeconds() && eventTime <= clip.getEndTimeSeconds()) {
				targetClipsByTrack.put(targetTrack.getId(), clip);
				return clip;
			}
		}

		double start = Math.max(0, anchorTime - 0.05);
		double end = Math.max(start + 0.2, start + span + 0.1);
		Clip created = TimelineOperations.addClip(targetTrack, start, end);
		if (created != null) {
			targetClipsByTrack.put(targetTrack.getId(), created);
			createdClips.add(new CreatedClipRef(targetTrack.getId(), created.getId()));
		}
		return created;
	}
}
