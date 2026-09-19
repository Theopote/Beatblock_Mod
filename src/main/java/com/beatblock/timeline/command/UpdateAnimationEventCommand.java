package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.AnimationEventSnapshot;
import com.beatblock.timeline.generation.TimelineEventOwnership;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 更新时间线事件属性、片段时间与相关元数据；支持 Undo/Redo。
 * <p>
 * Applying a user edit promotes {@code GENERATED} → {@code USER_EDITED}.
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
		apply(after, true);
	}

	@Override
	public void undo() {
		apply(before, false);
	}

	private void apply(@Nullable AnimationEventSnapshot snapshot, boolean promoteOnEdit) {
		if (timeline == null || snapshot == null) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent event = clip.getEvent(eventId);
		if (event == null) return;
		Map<String, Object> parameters = snapshot.parameters();
		if (promoteOnEdit && !parameters.isEmpty()) {
			parameters = TimelineEventOwnership.promoteOnUserEdit(parameters);
		}
		AnimationEventSnapshot toApply = parameters == snapshot.parameters()
			? snapshot
			: new AnimationEventSnapshot(
				snapshot.timeSeconds(),
				parameters,
				snapshot.clipStartSeconds(),
				snapshot.clipEndSeconds(),
				snapshot.clipEventTimesById(),
				snapshot.timelineMetadata(),
				snapshot.timelineDurationSeconds()
			);
		toApply.applyTo(event, clip, timeline);
		if (isAnimationTrack(trackId)) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	private static boolean isAnimationTrack(String trackId) {
		return Timeline.isAnimationEventsTrackId(trackId);
	}
}
