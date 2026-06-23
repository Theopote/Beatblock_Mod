package com.beatblock.timeline.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionBoxTest {

	@Test
	void minMaxNormalizeInvertedDrag() {
		SelectionBox box = new SelectionBox();
		box.setStart(10, 20);
		box.setEnd(5, 15);

		assertEquals(5f, box.getMinX(), 1e-6f);
		assertEquals(10f, box.getMaxX(), 1e-6f);
		assertEquals(15f, box.getMinY(), 1e-6f);
		assertEquals(20f, box.getMaxY(), 1e-6f);
	}

	@Test
	void containsPointInsideBounds() {
		SelectionBox box = new SelectionBox();
		box.setStart(0, 0);
		box.setEnd(100, 50);

		assertTrue(box.contains(50, 25));
		assertFalse(box.contains(101, 25));
	}
}
