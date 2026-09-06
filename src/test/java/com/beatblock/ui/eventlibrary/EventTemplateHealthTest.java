package com.beatblock.ui.eventlibrary;

import com.beatblock.engine.AnimationLibrary;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.ui.i18n.BBTexts;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class EventTemplateHealthTest {

	private final AnimationLibrary library = new AnimationLibrary();

	@Test
	void validWhenAnimationExistsInCatalog() {
		EventTemplate template = new EventTemplate(
			"t1", "Pulse", "Pulse", 0.35, 0.8f,
			Map.of("actionMode", "ANIMATE", "animationType", "Pulse", "eventOrigin", "MANUAL")
		);
		EventTemplateItem item = EventTemplateHealth.assess(template, library);
		assertEquals(EventTemplateStatus.VALID, item.status());
		assertTrue(item.canApply());
		assertTrue(item.warning().isBlank());
	}

	@Test
	void missingAnimationWhenTypeNotInCatalog() {
		EventTemplate template = new EventTemplate(
			"t2", "Old Wave", "WaveV1", 0.5, 0.7f,
			Map.of("actionMode", "ANIMATE", "animationType", "WaveV1")
		);
		EventTemplateItem item = EventTemplateHealth.assess(template, library);
		assertEquals(EventTemplateStatus.MISSING_ANIMATION, item.status());
		assertFalse(item.canApply());
		assertEquals(
			BBTexts.get("beatblock.event_library.health.missing_animation", "WaveV1"),
			item.warning()
		);
	}

	@Test
	void assessKeepsBrokenTemplatesVisibleInListSemantics() {
		EventTemplate broken = new EventTemplate(
			"keep-me", "Old Wave Preset", "WaveV1", 0.5, 0.7f,
			Map.of("actionMode", "ANIMATE", "animationType", "WaveV1")
		);
		EventTemplateItem item = EventTemplateHealth.assess(broken, library);
		assertEquals("Old Wave Preset", item.template().name());
		assertEquals(EventTemplateStatus.MISSING_ANIMATION, item.status());
		assertFalse(item.canApply());
		assertEquals(
			BBTexts.get("beatblock.event_library.health.missing_animation", "WaveV1"),
			item.warning()
		);
	}

	@Test
	void invalidWhenTypeFieldAndParameterDisagree() {
		EventTemplate template = new EventTemplate(
			"t3", "Mismatch", "Pulse", 0.35, 0.8f,
			Map.of("actionMode", "ANIMATE", "animationType", "BlockTap")
		);
		EventTemplateItem item = EventTemplateHealth.assess(template, library);
		assertEquals(EventTemplateStatus.INVALID_PARAMETERS, item.status());
		assertFalse(item.canApply());
	}

	@Test
	void invalidWhenAnimationTypeBlank() {
		EventTemplate template = new EventTemplate(
			"t4", "Blank", "", 0.35, 0.8f, Map.of("actionMode", "ANIMATE"));
		EventTemplateItem item = EventTemplateHealth.assess(template, library);
		assertEquals(EventTemplateStatus.INVALID_PARAMETERS, item.status());
		assertFalse(item.canApply());
	}

	@Test
	void legacyWhenGenerationIdentityStillPresent() {
		Map<String, Object> params = new HashMap<>();
		params.put("actionMode", "ANIMATE");
		params.put("animationType", "Pulse");
		params.put("eventOrigin", TimelineEventOrigin.MANUAL.name());
		params.put(TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID, "smart-automap");

		EventTemplate template = new EventTemplate("t5", "Legacy", "Pulse", 0.35, 0.8f, params);
		EventTemplateItem item = EventTemplateHealth.assess(template, library);
		assertEquals(EventTemplateStatus.LEGACY, item.status());
		assertTrue(item.canApply());
		assertEquals(BBTexts.get("beatblock.event_library.health.legacy"), item.warning());
	}
}
