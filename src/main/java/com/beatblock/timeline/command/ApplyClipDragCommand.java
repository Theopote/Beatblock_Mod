package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.ClipDragStateSnapshot;
import com.beatblock.timeline.generation.TimelineEventOwnership;

import org.jspecify.annotations.NonNull;

/**
 * 片段拖动 / 缩放：execute 应用 after 快照，undo 恢复 before 快照。
 * <p>
 * Forward apply promotes {@code GENERATED} events on affected clips to {@code USER_EDITED}.
 */
public final class ApplyClipDragCommand implements MergeableCommand {

	private final Timeline timeline;
	private final ClipDragStateSnapshot before;
	private final ClipDragStateSnapshot after;
	private final long mergeAnchorMs;

	public ApplyClipDragCommand(
		@NonNull Timeline timeline,
		@NonNull ClipDragStateSnapshot before,
		@NonNull ClipDragStateSnapshot after
	) {
		this(timeline, before, after, System.currentTimeMillis());
	}

	ApplyClipDragCommand(
		@NonNull Timeline timeline,
		@NonNull ClipDragStateSnapshot before,
		@NonNull ClipDragStateSnapshot after,
		long mergeAnchorMs
	) {
		this.timeline = timeline;
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
		if (!(other instanceof ApplyClipDragCommand cmd)) return false;
		if (!CommandMergePolicy.withinMergeWindow(mergeAnchorMs, mergeWindowMs())) return false;
		if (!CommandMergePolicy.withinMergeWindow(cmd.mergeAnchorMs, cmd.mergeWindowMs())) return false;
		return timeline == cmd.timeline && after.equals(cmd.before);
	}

	@Override
	public @NonNull Command mergeWith(@NonNull Command other) {
		ApplyClipDragCommand cmd = (ApplyClipDragCommand) other;
		return new ApplyClipDragCommand(timeline, before, cmd.after, mergeAnchorMs);
	}

	@Override
	public void execute() {
		if (after != null) after.applyTo(timeline);
		promoteAffectedEvents();
	}

	@Override
	public void undo() {
		if (before != null) before.applyTo(timeline);
	}

	private void promoteAffectedEvents() {
		if (timeline == null || after == null) return;
		for (ClipDragStateSnapshot.ClipBounds bounds : after.clipsByKey().values()) {
			Track track = timeline.getTrack(bounds.trackId());
			if (track == null) continue;
			Clip clip = track.getClip(bounds.clipId());
			if (clip == null) continue;
			for (TimelineEvent event : clip.getEvents()) {
				if (event == null) continue;
				event.setParameters(TimelineEventOwnership.promoteOnUserEdit(event.getParameters()));
			}
			if (Timeline.isAnimationEventsTrackId(bounds.trackId())) {
				timeline.markAnimationEventsDirty(bounds.trackId());
			}
		}
	}
}
