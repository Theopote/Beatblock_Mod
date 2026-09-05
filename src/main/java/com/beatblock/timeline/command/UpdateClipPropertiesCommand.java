package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.AnimationEventSnapshot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Clip-only property edit: timing, child event time shifts, and timeline metadata.
 * Does not require a {@link com.beatblock.timeline.TimelineEvent} on the clip
 * (Audio / empty Camera / BuildLayer clips).
 */
public final class UpdateClipPropertiesCommand implements MergeableCommand {

	private final Timeline timeline;
	private final String trackId;
	private final String clipId;
	private final AnimationEventSnapshot before;
	private final AnimationEventSnapshot after;
	private final long mergeAnchorMs;

	public UpdateClipPropertiesCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull AnimationEventSnapshot before,
		@NonNull AnimationEventSnapshot after
	) {
		this(timeline, trackId, clipId, before, after, System.currentTimeMillis());
	}

	UpdateClipPropertiesCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull AnimationEventSnapshot before,
		@NonNull AnimationEventSnapshot after,
		long mergeAnchorMs
	) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.clipId = clipId;
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
		if (!(other instanceof UpdateClipPropertiesCommand cmd)) return false;
		if (!CommandMergePolicy.withinMergeWindow(mergeAnchorMs, mergeWindowMs())) return false;
		if (!CommandMergePolicy.withinMergeWindow(cmd.mergeAnchorMs, cmd.mergeWindowMs())) return false;
		return timeline == cmd.timeline
			&& trackId.equals(cmd.trackId)
			&& clipId.equals(cmd.clipId);
	}

	@Override
	public @NonNull Command mergeWith(@NonNull Command other) {
		UpdateClipPropertiesCommand cmd = (UpdateClipPropertiesCommand) other;
		return new UpdateClipPropertiesCommand(
			timeline, trackId, clipId, before, cmd.after, mergeAnchorMs);
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
		snapshot.applyTo(null, clip, timeline);
		if (Timeline.isAnimationEventsTrackId(trackId)
			|| Timeline.TRACK_ID_CAMERA.equals(trackId)
			|| Timeline.TRACK_ID_GLOBAL.equals(trackId)
			|| Timeline.TRACK_ID_AUDIO.equals(trackId)
			|| com.beatblock.timeline.layer.BuildLayerTrackSupport.isBuildLayerTrackId(trackId)) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}
}
