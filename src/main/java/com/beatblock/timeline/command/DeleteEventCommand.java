package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

import org.jspecify.annotations.NonNull;

/**
 * 删除事件：execute 从 Clip 移除事件，undo 加回。
 */
public final class DeleteEventCommand implements Command {

	private final Timeline timeline;
	private final String trackId;
	private final String clipId;
	private final TimelineEvent event;
	private boolean done;

	public DeleteEventCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull TimelineEvent event
	) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.clipId = clipId;
		this.event = event;
	}

	@Override
	public void execute() {
		if (timeline == null || event == null || done) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip != null) {
			clip.removeEvent(event.getId());
			done = true;
		}
	}

	@Override
	public void undo() {
		if (!done) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip != null) clip.addEvent(event);
		done = false;
	}
}
