package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
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
	) {
		public ClipboardEvent {
			parameters = immutableMapCopy(parameters);
		}

		@Override
		public Map<String, Object> parameters() {
			return immutableMapCopy(parameters);
		}
	}

	public record PasteRequest(
		Timeline timeline,
		SelectionState selectionState,
		List<ClipboardEvent> clipboard,
		double anchorTimeSeconds,
		String contextTrackId,
		String contextClipId,
		TimelineTrackListState trackListState
	) {
		public PasteRequest {
			clipboard = clipboard != null ? List.copyOf(clipboard) : List.of();
		}
	}

	public static void copy(List<ClipboardEvent> target, Timeline timeline, SelectionState selectionState) {
		copy(target, timeline, selectionState, null, false);
	}

	/**
	 * Copy selection into {@code target}.
	 * When {@code skipLocked} is true, locked-track content is omitted (used by Cut/Duplicate so
	 * clipboard matches what Delete will remove / what Paste can accept).
	 */
	public static void copy(
		List<ClipboardEvent> target,
		Timeline timeline,
		SelectionState selectionState,
		@Nullable TimelineTrackListState trackListState,
		boolean skipLocked
	) {
		target.clear();
		if (timeline == null || selectionState == null) return;
		Set<String> selectedEvents = new HashSet<>(selectionState.getSelectedEvents());
		Set<String> selectedClips = new HashSet<>(selectionState.getSelectedClips());
		if (selectedEvents.isEmpty() && selectedClips.isEmpty()) return;
		for (Track track : timeline.getTracks()) {
			if (skipLocked && TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, track.getId())) {
				continue;
			}
			for (Clip clip : track.getClips()) {
				boolean clipSelected = selectedClips.contains(clip.getId());
				for (TimelineEvent e : clip.getEvents()) {
					if (!selectedEvents.contains(e.getId()) && !clipSelected) continue;
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
		Map<String, ModifiedClipBounds> modifiedClips = new HashMap<>();
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
				modifiedClips,
				request.contextTrackId(),
				request.contextClipId(),
				createdClips);
			if (targetClip == null) continue;
			Map<String, Object> pasteParams = parametersForPaste(src.parameters);
			TimelineEvent added = TimelineOperations.addEvent(targetClip, newTime, src.type, pasteParams);
			if (added != null) {
				selectionState.selectEvent(added.getId());
				dirtyTracks.add(targetTrack.getId());
				pasted.add(new PastedEventRef(targetTrack.getId(), targetClip.getId(), cloneEvent(added)));
			}
		}

		for (String trackId : dirtyTracks) {
			timeline.markAnimationEventsDirty(trackId);
		}
		return new PasteResult(pasted, createdClips, List.copyOf(modifiedClips.values()));
	}

	public record PastedEventRef(String trackId, String clipId, TimelineEvent event) {}

	public record CreatedClipRef(
		String trackId,
		String clipId,
		double startTimeSeconds,
		double endTimeSeconds
	) {}

	/** Existing clip whose bounds were expanded to fit pasted events. */
	public record ModifiedClipBounds(
		String trackId,
		String clipId,
		double originalStartSeconds,
		double originalEndSeconds,
		double newStartSeconds,
		double newEndSeconds
	) {}

	public record PasteResult(
		List<PastedEventRef> pastedEvents,
		List<CreatedClipRef> createdClips,
		List<ModifiedClipBounds> modifiedClips
	) {
		public PasteResult {
			pastedEvents = pastedEvents != null ? List.copyOf(pastedEvents) : List.of();
			createdClips = createdClips != null ? List.copyOf(createdClips) : List.of();
			modifiedClips = modifiedClips != null ? List.copyOf(modifiedClips) : List.of();
		}

		public PasteResult(List<PastedEventRef> pastedEvents, List<CreatedClipRef> createdClips) {
			this(pastedEvents, createdClips, List.of());
		}

		public static PasteResult empty() {
			return new PasteResult(List.of(), List.of(), List.of());
		}

		public boolean isEmpty() {
			return pastedEvents.isEmpty();
		}
	}

	/** Whether {@code type} may be pasted onto {@code track}. */
	public static boolean isEventCompatibleWithTrack(@Nullable EventType type, @Nullable Track track) {
		if (type == null || track == null) return false;
		return switch (type) {
			case CAMERA_KEYFRAME, CAMERA_SEGMENT ->
				track.getType() == TrackType.CAMERA || Timeline.TRACK_ID_CAMERA.equals(track.getId());
			case GLOBAL ->
				track.getType() == TrackType.EVENT || Timeline.TRACK_ID_GLOBAL.equals(track.getId());
			case ANIMATION, BEAT ->
				track.getType() == TrackType.ANIMATION || track.getType() == TrackType.BUILD_LAYER;
			case PARTICLE -> track.getType() == TrackType.PARTICLE;
			case LIGHTING ->
				track.getType() == TrackType.EVENT || Timeline.TRACK_ID_GLOBAL.equals(track.getId());
		};
	}

	/**
	 * Paste/Duplicate must not create a second live BuildLayer binding claim.
	 * Strips {@code layerBound}/{@code layerId} while keeping stageObjectId for a usable BUILD copy.
	 * Remints GENERATED/IMPORTED origins to MANUAL so content-replace pipelines do not wipe user paste.
	 */
	static Map<String, Object> parametersForPaste(@Nullable Map<String, Object> source) {
		Map<String, Object> copy = source == null ? new HashMap<>() : new HashMap<>(source);
		Object bound = copy.get("layerBound");
		if (bound != null && "true".equalsIgnoreCase(String.valueOf(bound).trim())) {
			copy.remove("layerBound");
			copy.remove("layerId");
		}
		var meta = TimelineGenerationMetadata.fromParameters(copy);
		if (meta.origin().isGenerated() || meta.origin().isImported()) {
			return TimelineGenerationMetadataSupport.apply(copy, TimelineGenerationMetadata.manual());
		}
		return copy;
	}

	private static TimelineEvent cloneEvent(TimelineEvent source) {
		return new TimelineEvent(
			source.getId(),
			source.getTimeSeconds(),
			source.getType(),
			new HashMap<>(source.getParameters())
		);
	}

	private static <K, V> Map<K, V> immutableMapCopy(Map<K, V> source) {
		return source == null || source.isEmpty()
			? Map.of()
			: Collections.unmodifiableMap(new HashMap<>(source));
	}

	private static Track resolvePasteTargetTrack(
		Timeline timeline,
		ClipboardEvent src,
		TimelineTrackListState trackListState,
		String contextTrackId
	) {
		if (contextTrackId != null) {
			Track context = timeline.getTrack(contextTrackId);
			if (context != null
				&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, context.getId())
				&& isEventCompatibleWithTrack(src.type, context)) {
				return context;
			}
		}
		Track fallback = timeline.getTrack(src.trackId);
		if (fallback == null) return null;
		if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, fallback.getId())) {
			return null;
		}
		return isEventCompatibleWithTrack(src.type, fallback) ? fallback : null;
	}

	private static Clip resolveOrCreatePasteTargetClip(
		Timeline timeline,
		Track targetTrack,
		double eventTime,
		double anchorTime,
		double span,
		Map<String, Clip> targetClipsByTrack,
		Map<String, ModifiedClipBounds> modifiedClips,
		String contextTrackId,
		String contextClipId,
		List<CreatedClipRef> createdClips
	) {
		Clip cached = targetClipsByTrack.get(targetTrack.getId());
		if (cached != null) {
			expandClipToInclude(targetTrack, cached, eventTime, modifiedClips);
			return cached;
		}

		if (contextTrackId != null && contextTrackId.equals(targetTrack.getId()) && contextClipId != null) {
			Clip contextClip = targetTrack.getClip(contextClipId);
			if (contextClip != null) {
				expandClipToInclude(targetTrack, contextClip, eventTime, modifiedClips);
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
		start = Math.min(start, eventTime);
		end = Math.max(end, eventTime);
		Clip created = TimelineOperations.addClip(targetTrack, start, end);
		if (created != null) {
			targetClipsByTrack.put(targetTrack.getId(), created);
			createdClips.add(new CreatedClipRef(
				targetTrack.getId(), created.getId(), start, end));
		}
		return created;
	}

	private static void expandClipToInclude(
		Track track,
		Clip clip,
		double eventTime,
		Map<String, ModifiedClipBounds> modifiedClips
	) {
		double start = clip.getStartTimeSeconds();
		double end = clip.getEndTimeSeconds();
		if (eventTime >= start && eventTime <= end) {
			return;
		}
		String key = track.getId() + "\0" + clip.getId();
		ModifiedClipBounds existing = modifiedClips.get(key);
		double originalStart = existing != null ? existing.originalStartSeconds() : start;
		double originalEnd = existing != null ? existing.originalEndSeconds() : end;
		double newStart = Math.min(start, eventTime);
		double newEnd = Math.max(end, eventTime);
		clip.setStartTimeSeconds(newStart);
		clip.setEndTimeSeconds(newEnd);
		modifiedClips.put(key, new ModifiedClipBounds(
			track.getId(), clip.getId(), originalStart, originalEnd, newStart, newEnd));
	}
}
