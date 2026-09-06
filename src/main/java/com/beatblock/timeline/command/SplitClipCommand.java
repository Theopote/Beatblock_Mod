package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.SelectionState;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits a clip at {@code splitTimeSeconds} into left [start, split] and right [split, end].
 * Events at/before the split stay on the left; events after move to the right clip.
 * <p>
 * Redo reuses the same right-clip id captured on first apply.
 */
public final class SplitClipCommand implements Command {

	public static final double MIN_SIDE_DURATION_SECONDS = 0.05;

	private final Timeline timeline;
	private final String trackId;
	private final String clipId;
	private final double splitTimeSeconds;
	private final @Nullable SelectionState selectionState;

	private boolean applied;
	private boolean snapshotCaptured;
	private double originalEndSeconds;
	private String rightClipId;

	public SplitClipCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull String clipId,
		double splitTimeSeconds,
		@Nullable SelectionState selectionState
	) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.clipId = clipId;
		this.splitTimeSeconds = splitTimeSeconds;
		this.selectionState = selectionState;
	}

	public static boolean canSplit(@Nullable Clip clip, double splitTimeSeconds) {
		if (clip == null) return false;
		return splitTimeSeconds >= clip.getStartTimeSeconds() + MIN_SIDE_DURATION_SECONDS
			&& splitTimeSeconds <= clip.getEndTimeSeconds() - MIN_SIDE_DURATION_SECONDS;
	}

	@Override
	public void execute() {
		if (applied || timeline == null) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip left = track.getClip(clipId);
		if (!canSplit(left, splitTimeSeconds)) return;

		if (!snapshotCaptured) {
			originalEndSeconds = left.getEndTimeSeconds();
			rightClipId = nextId();
			snapshotCaptured = true;
		}

		List<TimelineEvent> toMove = new ArrayList<>();
		for (TimelineEvent event : left.getEvents()) {
			if (event.getTimeSeconds() > splitTimeSeconds) {
				toMove.add(copyEvent(event));
			}
		}

		Clip right = track.getClip(rightClipId);
		if (right == null) {
			right = new Clip(rightClipId, splitTimeSeconds, originalEndSeconds);
			track.addClip(right);
		} else {
			right.setStartTimeSeconds(splitTimeSeconds);
			right.setEndTimeSeconds(originalEndSeconds);
		}

		for (TimelineEvent event : toMove) {
			left.removeEvent(event.getId());
			if (right.getEvent(event.getId()) == null) {
				right.addEvent(copyEvent(event));
			}
		}
		left.setEndTimeSeconds(splitTimeSeconds);
		timeline.markAnimationEventsDirty(trackId);

		if (selectionState != null) {
			selectionState.clearClips();
			selectionState.selectClip(clipId);
			selectionState.selectClip(rightClipId);
		}
		applied = true;
	}

	@Override
	public void undo() {
		if (!applied || timeline == null) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip left = track.getClip(clipId);
		Clip right = rightClipId != null ? track.getClip(rightClipId) : null;
		if (left == null) return;

		if (right != null) {
			for (TimelineEvent event : new ArrayList<>(right.getEvents())) {
				if (left.getEvent(event.getId()) == null) {
					left.addEvent(copyEvent(event));
				}
			}
			track.removeClip(rightClipId);
		}
		left.setEndTimeSeconds(originalEndSeconds);
		timeline.markAnimationEventsDirty(trackId);
		if (selectionState != null && rightClipId != null) {
			selectionState.deselectClip(rightClipId);
			selectionState.selectClip(clipId);
		}
		applied = false;
	}

	public boolean wasApplied() {
		return applied;
	}

	public @Nullable String rightClipId() {
		return rightClipId;
	}

	private static TimelineEvent copyEvent(TimelineEvent source) {
		Map<String, Object> params = new HashMap<>(source.getParameters());
		return new TimelineEvent(source.getId(), source.getTimeSeconds(), source.getType(), params);
	}

	private static String nextId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}
}
