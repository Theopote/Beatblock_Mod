package com.beatblock.timeline.command.layer;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;

/** 删除空的建造图层轨道。 */
public final class DeleteBuildLayerTrackCommand implements com.beatblock.timeline.command.Command {

	private final Timeline timeline;
	private final String trackId;
	private Track removedTrackSnapshot;

	public DeleteBuildLayerTrackCommand(Timeline timeline, String trackId) {
		this.timeline = timeline;
		this.trackId = trackId;
	}

	@Override
	public void execute() {
		if (timeline == null || trackId == null) {
			return;
		}
		Track track = timeline.getTrack(trackId);
		if (track == null || !BuildLayerTrackSupport.isBuildLayerTrack(track)) {
			return;
		}
		if (!track.getClips().isEmpty()) {
			return;
		}
		if (BuildLayerTrackSupport.listTracks(timeline).size() <= 1) {
			return;
		}
		removedTrackSnapshot = new Track(track.getId(), track.getName(), TrackType.BUILD_LAYER);
		timeline.removeTrack(trackId);
	}

	@Override
	public void undo() {
		if (timeline == null || removedTrackSnapshot == null) {
			return;
		}
		timeline.addTrack(removedTrackSnapshot);
		removedTrackSnapshot = null;
	}
}
