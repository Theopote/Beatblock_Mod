package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Undoable camera-track write: runs a factory/writer once, then undoes by removing the created clip.
 * <p>
 * Used by Creator Camera Shot inserts and Timeline context-menu segment creation.
 */
public final class CreateCameraClipCommand implements Command {

	private final Timeline timeline;
	private final Consumer<Timeline> write;
	private @Nullable String createdClipId;
	private double previousDurationSeconds;
	private boolean done;

	public CreateCameraClipCommand(Timeline timeline, Consumer<Timeline> write) {
		this.timeline = Objects.requireNonNull(timeline, "timeline");
		this.write = Objects.requireNonNull(write, "write");
	}

	public @Nullable String createdClipId() {
		return done ? createdClipId : null;
	}

	public boolean wasApplied() {
		return done && createdClipId != null;
	}

	@Override
	public void execute() {
		if (done) {
			return;
		}
		Track track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (track == null) {
			return;
		}
		previousDurationSeconds = timeline.getDurationSeconds();
		Set<String> before = clipIds(track);
		write.accept(timeline);
		Track afterTrack = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (afterTrack == null) {
			return;
		}
		String created = null;
		for (Clip clip : afterTrack.getClips()) {
			if (clip != null && clip.getId() != null && !before.contains(clip.getId())) {
				created = clip.getId();
				break;
			}
		}
		if (created == null) {
			return;
		}
		createdClipId = created;
		done = true;
	}

	@Override
	public void undo() {
		if (!done || createdClipId == null) {
			return;
		}
		Track track = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (track != null) {
			track.removeClip(createdClipId);
		}
		timeline.setDurationSeconds(previousDurationSeconds);
		createdClipId = null;
		done = false;
	}

	private static Set<String> clipIds(Track track) {
		Set<String> ids = new HashSet<>();
		for (Clip clip : track.getClips()) {
			if (clip != null && clip.getId() != null) {
				ids.add(clip.getId());
			}
		}
		return ids;
	}
}
