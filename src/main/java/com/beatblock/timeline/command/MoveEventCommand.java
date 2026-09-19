package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.generation.TimelineEventOwnership;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 移动事件时间：execute 设为 newTime，undo 恢复 oldTime。
 * <p>
 * Forward apply promotes {@code GENERATED} → {@code USER_EDITED}; undo restores prior parameters.
 */
public final class MoveEventCommand implements MergeableCommand {

	private static final double TIME_EPSILON = 1e-9;

	private final Timeline timeline;
	private final String trackId;
	private final String clipId;
	private final String eventId;
	private final double oldTimeSeconds;
	private final double newTimeSeconds;
	private final long mergeAnchorMs;
	private @Nullable Map<String, Object> parametersBeforePromote;

	public MoveEventCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull String eventId,
		double oldTimeSeconds,
		double newTimeSeconds
	) {
		this(timeline, trackId, clipId, eventId, oldTimeSeconds, newTimeSeconds, System.currentTimeMillis());
	}

	MoveEventCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull String eventId,
		double oldTimeSeconds,
		double newTimeSeconds,
		long mergeAnchorMs
	) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.clipId = clipId;
		this.eventId = eventId;
		this.oldTimeSeconds = oldTimeSeconds;
		this.newTimeSeconds = newTimeSeconds;
		this.mergeAnchorMs = mergeAnchorMs;
	}

	@Override
	public long mergeWindowMs() {
		return CommandMergePolicy.DEFAULT_MERGE_WINDOW_MS;
	}

	@Override
	public boolean canMergeWith(Command other) {
		if (!(other instanceof MoveEventCommand cmd)) return false;
		if (!CommandMergePolicy.withinMergeWindow(mergeAnchorMs, mergeWindowMs())) return false;
		if (!CommandMergePolicy.withinMergeWindow(cmd.mergeAnchorMs, cmd.mergeWindowMs())) return false;
		if (timeline != cmd.timeline
			|| !trackId.equals(cmd.trackId)
			|| !clipId.equals(cmd.clipId)
			|| !eventId.equals(cmd.eventId)) {
			return false;
		}
		return Math.abs(newTimeSeconds - cmd.oldTimeSeconds) <= TIME_EPSILON;
	}

	@Override
	public @NonNull Command mergeWith(@NonNull Command other) {
		MoveEventCommand cmd = (MoveEventCommand) other;
		MoveEventCommand merged = new MoveEventCommand(
			timeline, trackId, clipId, eventId, oldTimeSeconds, cmd.newTimeSeconds, mergeAnchorMs);
		merged.parametersBeforePromote = this.parametersBeforePromote;
		return merged;
	}

	@Override
	public void execute() {
		apply(newTimeSeconds, true);
	}

	@Override
	public void undo() {
		apply(oldTimeSeconds, false);
	}

	private void apply(double timeSeconds, boolean promote) {
		if (timeline == null) return;
		var track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent e = clip.getEvent(eventId);
		if (e == null) return;
		e.setTimeSeconds(timeSeconds);
		if (promote) {
			if (parametersBeforePromote == null) {
				parametersBeforePromote = Map.copyOf(e.getParameters());
			}
			e.setParameters(TimelineEventOwnership.promoteOnUserEdit(e.getParameters()));
		} else if (parametersBeforePromote != null) {
			e.setParameters(parametersBeforePromote);
		}
		if (isAnimationTrack(trackId)) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	private static boolean isAnimationTrack(String trackId) {
		return Timeline.isAnimationEventsTrackId(trackId);
	}
}
