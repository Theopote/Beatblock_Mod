package com.beatblock.selection.tools;

import com.beatblock.selection.SelectionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SelectionToolRegistryTest {

	@Test
	void registersAllClickDispatchModes() {
		var handlers = SelectionToolRegistry.handlersForTests();

		assertNotNull(handlers.get(SelectionMode.CLICK));
		assertNotNull(handlers.get(SelectionMode.BOX));
		assertNotNull(handlers.get(SelectionMode.LINE));
		assertNotNull(handlers.get(SelectionMode.BRUSH));
		assertNotNull(handlers.get(SelectionMode.CONNECTED));
		assertNotNull(handlers.get(SelectionMode.COLUMN));
		assertNotNull(handlers.get(SelectionMode.PLANE_SLICE));
		assertNotNull(handlers.get(SelectionMode.SELECTION_WAND));
		assertNull(handlers.get(SelectionMode.OFF));
		assertNull(handlers.get(SelectionMode.LASSO));
		assertEquals(8, handlers.size());
	}
}
