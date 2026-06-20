package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 清空指定动画轨道上的全部 clip / 事件，支持 Undo。
 */
public final class ClearAnimationTrackCommand implements Command {

	private record Snapshot(String clipId, double clipStart, double clipEnd, List<TimelineEvent> events) {}

	private final Timeline timeline;
	private final String trackId;
	private final List<Snapshot> snapshots = new ArrayList<>();
	private boolean done;

	public ClearAnimationTrackCommand(Timeline timeline, String trackId) {
		this.timeline = timeline;
		this.trackId = trackId;
	}

	@Override
	public void execute() {
		if (timeline == null || trackId == null || done) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		snapshots.clear();
		for (Clip clip : new ArrayList<>(track.getClips())) {
			List<TimelineEvent> events = new ArrayList<>();
			for (TimelineEvent event : clip.getEvents()) {
				if (event.getType() == EventType.ANIMATION) {
					events.add(copyEvent(event));
				}
			}
			if (!events.isEmpty()) {
				snapshots.add(new Snapshot(clip.getId(), clip.getStartTimeSeconds(), clip.getEndTimeSeconds(), events));
			}
			track.removeClip(clip.getId());
		}
		timeline.markAnimationEventsDirty(trackId);
		done = true;
	}

	@Override
	public void undo() {
		if (!done || timeline == null) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		for (Snapshot snapshot : snapshots) {
			Clip clip = TimelineOperations.addClip(track, snapshot.clipStart(), snapshot.clipEnd());
			if (clip == null) continue;
			for (TimelineEvent event : snapshot.events()) {
				TimelineOperations.addEvent(clip, event.getTimeSeconds(), event.getType(), new HashMap<>(event.getParameters()));
			}
		}
		timeline.markAnimationEventsDirty(trackId);
		done = false;
	}

	private static TimelineEvent copyEvent(TimelineEvent source) {
		return new TimelineEvent(
			source.getId(),
			source.getTimeSeconds(),
			source.getType(),
			new HashMap<>(source.getParameters())
		);
	}
}
