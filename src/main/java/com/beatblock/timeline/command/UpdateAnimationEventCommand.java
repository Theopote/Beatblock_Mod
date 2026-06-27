package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.AnimationEventSnapshot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 更新时间线事件属性、片段时间与相关元数据；支持 Undo/Redo。
 */
public final class UpdateAnimationEventCommand implements MergeableCommand {

	private final Timeline timeline;
	private final String trackId;
	private final String clipId;
	private final String eventId;
	private final AnimationEventSnapshot before;
	private final AnimationEventSnapshot after;
	private final long mergeAnchorMs;

	public UpdateAnimationEventCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull String eventId,
		@NonNull AnimationEventSnapshot before,
		@NonNull AnimationEventSnapshot after
	) {
		this(timeline, trackId, clipId, eventId, before, after, System.currentTimeMillis());
	}

	UpdateAnimationEventCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull String eventId,
		@NonNull AnimationEventSnapshot before,
		@NonNull AnimationEventSnapshot after,
		long mergeAnchorMs
	) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.clipId = clipId;
		this.eventId = eventId;
		this.before = before;
		this.after = after;
		this.mergeAnchorMs = mergeAnchorMs;
	}

	@Override
	public long mergeWindowMs() {
		return CommandMergePolicy.DEFAULT_MERGE_WINDOW_MS;
	}

	@Override
	public boolean canMergeWith(Command other) {
		if (!(other instanceof UpdateAnimationEventCommand cmd)) return false;
		if (!CommandMergePolicy.withinMergeWindow(mergeAnchorMs, mergeWindowMs())) return false;
		if (!CommandMergePolicy.withinMergeWindow(cmd.mergeAnchorMs, cmd.mergeWindowMs())) return false;
		return timeline == cmd.timeline
			&& trackId.equals(cmd.trackId)
			&& clipId.equals(cmd.clipId)
			&& eventId.equals(cmd.eventId);
	}

	@Override
	public @NonNull Command mergeWith(@NonNull Command other) {
		UpdateAnimationEventCommand cmd = (UpdateAnimationEventCommand) other;
		return new UpdateAnimationEventCommand(
			timeline, trackId, clipId, eventId, before, cmd.after, mergeAnchorMs);
	}

	@Override
	public void execute() {
		apply(after);
	}

	@Override
	public void undo() {
		apply(before);
	}

	private void apply(@Nullable AnimationEventSnapshot snapshot) {
		if (timeline == null || snapshot == null) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent event = clip.getEvent(eventId);
		if (event == null && !clip.getEvents().isEmpty()) {
			event = clip.getEvents().get(0);
		}
		if (event == null) return;
		snapshot.applyTo(event, clip, timeline);
		if (isAnimationTrack(trackId)) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	private static boolean isAnimationTrack(String trackId) {
		return Timeline.TRACK_ID_ANIMATION_BLOCK.equals(trackId)
			|| Timeline.TRACK_ID_ANIMATION_AUTO.equals(trackId)
			|| Timeline.TRACK_ID_BUILD_REVERSE.equals(trackId)
			|| Timeline.isBlockAnimationFeatureTrackId(trackId);
	}
}
