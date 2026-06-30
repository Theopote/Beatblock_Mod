package com.beatblock.timeline.command.layer;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;

/** 新建一条空的建造图层轨道。 */
public final class CreateBuildLayerTrackCommand implements com.beatblock.timeline.command.Command {

	private final Timeline timeline;
	private String createdTrackId;
	private String createdTrackName;

	public CreateBuildLayerTrackCommand(Timeline timeline) {
		this(timeline, null);
	}

	public CreateBuildLayerTrackCommand(Timeline timeline, String requestedName) {
		this.timeline = timeline;
		this.createdTrackName = requestedName;
	}

	@Override
	public void execute() {
		if (timeline == null || !BuildLayerTrackSupport.canCreateMoreTracks(timeline)) {
			return;
		}
		createdTrackId = BuildLayerTrackSupport.nextTrackId(timeline);
		String name = createdTrackName != null && !createdTrackName.isBlank()
			? createdTrackName.trim()
			: BuildLayerTrackSupport.nextDefaultTrackName(timeline);
		createdTrackName = name;
		Track track = new Track(createdTrackId, name, TrackType.BUILD_LAYER);
		timeline.addTrack(track);
	}

	@Override
	public void undo() {
		if (timeline == null || createdTrackId == null) {
			return;
		}
		timeline.removeTrack(createdTrackId);
		createdTrackId = null;
	}

	public String getCreatedTrackId() {
		return createdTrackId;
	}
}
