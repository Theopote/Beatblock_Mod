package com.beatblock.ui.labels;

import com.beatblock.engine.GroupSortingStrategy;
import com.beatblock.engine.GroupSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BlockOrderLabelsTest {

	@Test
	void sortingStrategyLabelUsesUserFacingKeys() {
		assertFalse(BlockOrderLabels.sortingStrategyLabel(GroupSortingStrategy.SEQUENTIAL).isBlank());
		assertFalse(BlockOrderLabels.sortingStrategyLabel(GroupSortingStrategy.RADIAL).isBlank());
	}

	@Test
	void sourceTypeLabelMapsKnownTypes() {
		assertFalse(BlockOrderLabels.sourceTypeLabel("selection_snapshot").isBlank());
		assertFalse(BlockOrderLabels.sourceTypeLabel(GroupSpec.manualSnapshot()).isBlank());
	}

	@Test
	void sortingComboLabelsMatchStrategyCount() {
		assertEquals(GroupSortingStrategy.values().length, BlockOrderLabels.sortingComboLabels().length);
	}
}
