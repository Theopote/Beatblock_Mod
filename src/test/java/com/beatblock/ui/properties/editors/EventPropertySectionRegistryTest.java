package com.beatblock.ui.properties.editors;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPropertySectionRegistryTest {

	@Test
	void defaultRegistryCoversAllTabsWithExpectedBuiltIns() {
		EventPropertySectionRegistry registry = EventPropertySectionRegistry.createDefault();
		List<EventPropertySection> all = registry.all();
		assertTrue(all.size() >= 10, "expected full built-in section set, got " + all.size());

		Set<EventPropertySection.Tab> tabs = EnumSet.noneOf(EventPropertySection.Tab.class);
		for (EventPropertySection section : all) {
			tabs.add(section.tab());
		}
		assertEquals(EnumSet.allOf(EventPropertySection.Tab.class), tabs);
	}

	@Test
	void forTabReturnsOnlyMatchingSectionsInOrder() {
		EventPropertySectionRegistry registry = EventPropertySectionRegistry.createDefault();
		List<EventPropertySection> basic = registry.forTab(EventPropertySection.Tab.BASIC);
		assertTrue(basic.size() >= 4);
		for (int i = 1; i < basic.size(); i++) {
			assertTrue(
				basic.get(i - 1).order() <= basic.get(i).order(),
				"BASIC sections should be sorted by order()"
			);
		}
		assertTrue(basic.stream().allMatch(s -> s.tab() == EventPropertySection.Tab.BASIC));
	}

	@Test
	void registerAddsCustomSection() {
		EventPropertySectionRegistry registry = EventPropertySectionRegistry.createDefault();
		int before = registry.all().size();
		registry.register(new EventPropertySection() {
			@Override
			public Tab tab() {
				return Tab.INFO;
			}

			@Override
			public int order() {
				return 999;
			}

			@Override
			public boolean supports(EventEditContext context) {
				return false;
			}

			@Override
			public void render(EventEditContext context) {
			}
		});
		assertEquals(before + 1, registry.all().size());
		assertTrue(registry.forTab(EventPropertySection.Tab.INFO).stream()
			.anyMatch(s -> s.order() == 999));
	}
}
