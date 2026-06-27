package com.beatblock.automap.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructuralSectionTest {

	@Test
	void clampsStartEndAndDefaultsType() {
		StructuralSection section = new StructuralSection(-2.0, 1.0, null);
		assertEquals(0.0, section.getStartSeconds(), 1e-9);
		assertEquals(1.0, section.getEndSeconds(), 1e-9);
		assertEquals(SectionType.VERSE, section.getType());
		assertEquals(1.0, section.getDurationSeconds(), 1e-9);
	}

	@Test
	void endCannotBeBeforeStart() {
		StructuralSection section = new StructuralSection(5.0, 3.0, SectionType.DROP);
		assertEquals(5.0, section.getStartSeconds(), 1e-9);
		assertEquals(5.0, section.getEndSeconds(), 1e-9);
		assertEquals(0.0, section.getDurationSeconds(), 1e-9);
	}
}
