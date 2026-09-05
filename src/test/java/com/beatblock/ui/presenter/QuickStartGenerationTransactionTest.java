package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.selection.SelectionMode;
import com.beatblock.selection.SelectionOperation;
import com.beatblock.test.BeatBlockTestSupport;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickStartGenerationTransactionTest {

	private Timeline timeline;
	private StageObjectSystem stageObjectSystem;
	private ToolPanelPresenter toolPanelPresenter;
	private BeatBlockSelectionManager selectionManager;

	@BeforeEach
	void setUp() {
		BeatBlock.installContext(BeatBlockTestSupport.minimalContext());
		timeline = BeatBlock.getContext().timeline();
		stageObjectSystem = BeatBlock.getContext().blockAnimationEngine().getStageObjectSystem();
		selectionManager = BeatBlock.getContext().selectionManager();
		selectionManager.reset();
		selectionManager.setMode(SelectionMode.LASSO);
		toolPanelPresenter = PresenterFactories.toolPanelPresenter(BeatBlock.getContext());
	}

	@AfterEach
	void tearDown() {
		try {
			selectionManager.reset();
		} catch (IllegalStateException ignored) {
			// context already cleared
		}
		BeatBlock.resetContext();
	}

	@Test
	void rollbackRemovesCreatedStageObjectAndRestoresTimeline() {
		Track autoTrack = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		Clip original = new Clip("keep-clip", 0.0, 2.0);
		original.addEvent(new TimelineEvent("keep-event", 0.5, EventType.ANIMATION, Map.of("targetObject", "tower")));
		autoTrack.addClip(original);
		timeline.markAnimationEventsDirty(Timeline.TRACK_ID_ANIMATION_AUTO);

		selectionManager.commitLassoSelection(List.of(
			new BlockPos(0, 64, 0),
			new BlockPos(1, 64, 0)
		), SelectionOperation.NEW);

		QuickStartGenerationTransaction tx = QuickStartGenerationTransaction.begin(timeline);

		var created = toolPanelPresenter.createFromSelectionSnapshot(
			new ToolPanelPresenter.StageObjectCreateRequest("orphan", false, GroupSortingStrategy.SEQUENTIAL, 0.0)
		);
		assertTrue(created.result().ok());
		tx.recordCreatedStageObject(created.objectId());
		assertEquals(2, stageObjectSystem.size());
		assertTrue(stageObjectSystem.get(created.objectId()) != null);

		autoTrack.addClip(new Clip("orphan-clip", 3.0, 4.0));
		timeline.markAnimationEventsDirty(Timeline.TRACK_ID_ANIMATION_AUTO);
		assertEquals(2, autoTrack.getClips().size());

		tx.rollback(toolPanelPresenter, timeline);

		assertTrue(tx.isRolledBack());
		assertEquals(1, stageObjectSystem.size());
		assertTrue(stageObjectSystem.get(created.objectId()) == null);
		assertEquals(1, autoTrack.getClips().size());
		assertEquals("keep-clip", autoTrack.getClips().get(0).getId());
	}

	@Test
	void commitSkipsRollback() {
		selectionManager.commitLassoSelection(List.of(new BlockPos(2, 64, 2)), SelectionOperation.NEW);
		QuickStartGenerationTransaction tx = QuickStartGenerationTransaction.begin(timeline);
		var created = toolPanelPresenter.createFromSelectionSnapshot(
			new ToolPanelPresenter.StageObjectCreateRequest("kept", false, GroupSortingStrategy.SEQUENTIAL, 0.0)
		);
		tx.recordCreatedStageObject(created.objectId());
		tx.commit();

		tx.rollback(toolPanelPresenter, timeline);

		assertTrue(tx.isCommitted());
		assertFalse(tx.isRolledBack());
		assertEquals(2, stageObjectSystem.size());
		assertTrue(stageObjectSystem.get(created.objectId()) != null);
	}

	@Test
	void rollbackRestoresPreGenerationTimelineEvenIfPartialWritesExist() {
		Track autoTrack = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		Clip keep = new Clip("keep-clip", 0.0, 1.0);
		keep.addEvent(new TimelineEvent("keep-event", 0.0, EventType.ANIMATION, Map.of("targetObject", "a")));
		autoTrack.addClip(keep);
		timeline.markAnimationEventsDirty(Timeline.TRACK_ID_ANIMATION_AUTO);

		QuickStartGenerationTransaction tx = QuickStartGenerationTransaction.begin(timeline);
		tx.recordGenerationId("gen-partial-batch");

		Map<String, Object> params = TimelineGenerationMetadataSupport.apply(
			Map.of("targetObject", "b"),
			new TimelineGenerationMetadata(
				TimelineEventOrigin.GENERATED, "smart-automap", "gen-partial-batch", -1, -1, ""
			)
		);
		Clip partial = new Clip("partial-clip", 2.0, 3.0);
		partial.addEvent(new TimelineEvent("partial-event", 2.0, EventType.ANIMATION, params));
		autoTrack.addClip(partial);
		timeline.markAnimationEventsDirty(Timeline.TRACK_ID_ANIMATION_AUTO);
		assertEquals(2, autoTrack.getClips().size());

		tx.rollback(toolPanelPresenter, timeline);

		assertEquals(1, autoTrack.getClips().size());
		assertEquals("keep-clip", autoTrack.getClips().get(0).getId());
		assertEquals("gen-partial-batch", tx.state().generationId());
	}
}
