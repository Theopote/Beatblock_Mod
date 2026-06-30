package com.beatblock.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiNumberFormatterTest {

	@Test
	void formatUsesTwoDecimalsByDefault() {
		assertEquals("2.50", UiNumberFormatter.format(2.5));
		assertEquals("0.93", UiNumberFormatter.format(0.93));
		assertEquals("1.23", UiNumberFormatter.format(1.234567));
	}

	@Test
	void formatParamValueHandlesNumbersAndOtherTypes() {
		assertEquals("8.00", UiNumberFormatter.formatParamValue(8.0));
		assertEquals("pulse", UiNumberFormatter.formatParamValue("pulse"));
		assertEquals("", UiNumberFormatter.formatParamValue(null));
	}
}
