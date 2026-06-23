package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimelineStageObjectTest {

	@Test
	void defaultsNullTypeToSingleBlock() {
		StageObject object = new StageObject("obj-1", null, null);
		assertEquals("obj-1", object.getId());
		assertEquals(StageObjectType.SINGLE_BLOCK, object.getType());
		assertEquals("obj-1", object.getName());
	}

	@Test
	void preservesExplicitTypeAndName() {
		StageObject object = new StageObject(
			"group-1", StageObjectType.BLOCK_GROUP, "Main Tower");
		assertEquals(StageObjectType.BLOCK_GROUP, object.getType());
		assertEquals("Main Tower", object.getName());
	}
}
