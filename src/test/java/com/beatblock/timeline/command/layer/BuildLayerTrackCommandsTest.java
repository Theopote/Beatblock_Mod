package com.beatblock.timeline.command.layer;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildLayerTrackCommandsTest {

	private Timeline timeline;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
	}

	@Test
	void createBuildLayerTrackAddsSecondTrack() {
		assertEquals(1, BuildLayerTrackSupport.listTracks(timeline).size());

		CreateBuildLayerTrackCommand create = new CreateBuildLayerTrackCommand(timeline);
		create.execute();

		assertEquals(2, BuildLayerTrackSupport.listTracks(timeline).size());
		assertNotNull(timeline.getTrack(create.getCreatedTrackId()));

		create.undo();
		assertEquals(1, BuildLayerTrackSupport.listTracks(timeline).size());
		assertNull(timeline.getTrack(create.getCreatedTrackId()));
	}

	@Test
	void deleteEmptyBuildLayerTrackRemovesTrack() {
		CreateBuildLayerTrackCommand create = new CreateBuildLayerTrackCommand(timeline);
		create.execute();
		String secondId = create.getCreatedTrackId();

		DeleteBuildLayerTrackCommand delete = new DeleteBuildLayerTrackCommand(timeline, secondId);
		delete.execute();
		assertNull(timeline.getTrack(secondId));

		delete.undo();
		assertNotNull(timeline.getTrack(secondId));
	}

	@Test
	void deleteBuildLayerTrackRejectsLastRemainingTrack() {
		String onlyId = BuildLayerTrackSupport.DEFAULT_FIRST_TRACK_ID;
		DeleteBuildLayerTrackCommand delete = new DeleteBuildLayerTrackCommand(timeline, onlyId);
		delete.execute();
		assertNotNull(timeline.getTrack(onlyId));
	}

	@Test
	void createBuildLayerTrackRespectsMaxSixteen() {
		while (BuildLayerTrackSupport.canCreateMoreTracks(timeline)) {
			new CreateBuildLayerTrackCommand(timeline).execute();
		}
		assertEquals(BuildLayerTrackSupport.MAX_TRACKS, BuildLayerTrackSupport.listTracks(timeline).size());

		CreateBuildLayerTrackCommand overflow = new CreateBuildLayerTrackCommand(timeline);
		overflow.execute();
		assertEquals(BuildLayerTrackSupport.MAX_TRACKS, BuildLayerTrackSupport.listTracks(timeline).size());
		assertNull(overflow.getCreatedTrackId());
	}
}
