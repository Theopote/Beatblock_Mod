package com.beatblock.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackExecutionModeTest {

	@Test
	void exportReconstructionUsesUnlimitedMutationBudget() {
		assertEquals(Integer.MAX_VALUE, PlaybackExecutionMode.EXPORT_RECONSTRUCTION.mutationBudgetPerTick());
		assertEquals(Integer.MAX_VALUE, PlaybackExecutionMode.EDITOR_PREVIEW.mutationBudgetPerTick());
		assertEquals(
			PlaybackExecutionMode.REALTIME_MUTATION_BUDGET_PER_TICK,
			PlaybackExecutionMode.REALTIME_PLAYBACK.mutationBudgetPerTick()
		);
	}

	@Test
	void exportReconstructionWritesWorldAndBlocksOrdinaryTick() {
		assertTrue(PlaybackExecutionMode.EXPORT_RECONSTRUCTION.writesAuthoritativeWorld());
		assertTrue(PlaybackExecutionMode.EXPORT_RECONSTRUCTION.blocksOrdinaryClientTick());
		assertFalse(PlaybackExecutionMode.EXPORT_RECONSTRUCTION.isPreviewOnly());

		assertFalse(PlaybackExecutionMode.EDITOR_PREVIEW.writesAuthoritativeWorld());
		assertTrue(PlaybackExecutionMode.EDITOR_PREVIEW.isPreviewOnly());
		assertFalse(PlaybackExecutionMode.EDITOR_PREVIEW.blocksOrdinaryClientTick());
	}
}
