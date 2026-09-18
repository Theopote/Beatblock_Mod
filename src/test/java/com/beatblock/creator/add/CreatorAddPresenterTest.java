package com.beatblock.creator.add;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.ui.presenter.PresenterFactories;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class CreatorAddPresenterTest {

	@Test
	void insertAnimationPresetAtPlayhead() {
		var context = com.beatblock.BeatBlock.getContext();
		Timeline timeline = context.timeline();
		TimelineEditor editor = context.timelineEditor();
		TimelineOperations.addClip(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK, 0, 30);

		CreatorAddPresenter presenter = PresenterFactories.creatorAddPresenter(context);
		CreatorAddPresenter.Outcome outcome = presenter.insertAnimationPreset("Pulse");

		assertTrue(outcome.success());
		assertTrue(timeline.getBlockAnimationEvents().size() >= 1);
	}
}
