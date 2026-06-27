package com.beatblock.selection;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.ui.i18n.BBTexts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class SelectionFeedbackTest {

	@Test
	void mergeAfterNewUsesModeSpecificLabel() {
		assertEquals(
			BBTexts.get("beatblock.selection.feedback.new.brush", BBTexts.get("beatblock.tool.shape.sphere"), 3),
			SelectionFeedback.mergeAfterNew(SelectionMode.BRUSH, BrushShape.SPHERE, 3)
		);
		assertEquals(
			BBTexts.get("beatblock.selection.feedback.new.box", 2),
			SelectionFeedback.mergeAfterNew(SelectionMode.BOX, BrushShape.SPHERE, 2)
		);
	}

	@Test
	void mergeAfterOperationDispatchesByOperation() {
		assertEquals(
			BBTexts.get("beatblock.selection.feedback.add.lasso", 5),
			SelectionFeedback.mergeAfterOperation(SelectionMode.LASSO, BrushShape.CUBE, SelectionOperation.ADD, 2, 5)
		);
		assertEquals(
			BBTexts.get("beatblock.selection.feedback.intersect.line", 1),
			SelectionFeedback.mergeAfterOperation(SelectionMode.LINE, BrushShape.SPHERE, SelectionOperation.INTERSECT, 0, 1)
		);
	}

	@Test
	void emptyMergeMessagePrefersLayerClaimedNotice() {
		assertEquals(
			BBTexts.get("beatblock.selection.feedback.all_in_layers"),
			SelectionFeedback.emptyMergeMessage(SelectionMode.BOX, BrushShape.SPHERE, 2)
		);
		assertEquals(
			BBTexts.get("beatblock.selection.feedback.new.column", 0),
			SelectionFeedback.emptyMergeMessage(SelectionMode.COLUMN, BrushShape.SPHERE, 0)
		);
	}

	@Test
	void appendSkippedLayerNoticeAppendsSuffix() {
		String base = BBTexts.get("beatblock.selection.feedback.new.box", 4);
		String merged = SelectionFeedback.appendSkippedLayerNotice(base, 3);
		assertTrue(merged.contains(BBTexts.get("beatblock.selection.feedback.skipped_layers", 3).trim()));
		assertEquals(base, SelectionFeedback.appendSkippedLayerNotice(base, 0));
	}
}
