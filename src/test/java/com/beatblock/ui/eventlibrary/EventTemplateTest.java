package com.beatblock.ui.eventlibrary;

import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventTemplateTest {

	@Test
	void roundTripPreservesAnimationFieldsWithoutTimeOrTarget() {
		TimelineAnimationEvent source = new TimelineAnimationEvent(
			"evt-1",
			12.5,
			0.8,
			"bounce",
			"obj_a",
			0.6f,
			Map.of("actionMode", "ANIMATE", "meteorHeight", 8.0)
		);
		EventTemplate template = EventTemplate.fromAnimationEvent(source, "My Bounce");
		TimelineAnimationEvent applied = template.toTimelineEvent(3.0, "obj_b");

		assertEquals("My Bounce", template.name());
		assertEquals("bounce", applied.getAnimationTypeId());
		assertEquals(3.0, applied.getTimeSeconds(), 1e-6);
		assertEquals("obj_b", applied.getTargetObjectId());
		assertEquals(0.8, applied.getDurationSeconds(), 1e-6);
		assertEquals(0.6f, applied.getEnergy(), 1e-6);
		assertEquals("ANIMATE", applied.getParameters().get("actionMode"));
		assertEquals(8.0, ((Number) applied.getParameters().get("meteorHeight")).doubleValue(), 1e-6);
		assertFalse(template.parameters().containsKey("targetObject"));
	}

	@Test
	void fromGeneratedEventRemintsAsManualAndStripsProvenance() {
		Map<String, Object> params = new HashMap<>();
		params.put("actionMode", "ANIMATE");
		params.put("eventOrigin", TimelineEventOrigin.GENERATED.name());
		params.put(TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID, "smart-automap");
		params.put(TimelineGenerationMetadataSupport.PARAM_GENERATION_ID, "gen-old");
		params.put(TimelineGenerationMetadataSupport.PARAM_SECTION_INDEX, 2);
		params.put(TimelineGenerationMetadataSupport.PARAM_PHRASE_INDEX, 4);
		params.put(TimelineGenerationMetadataSupport.PARAM_SOURCE_PLAN_ID, "plan-1");
		params.put("generatedBy", "AutoMap");
		params.put("sourceFeature", "onset");
		params.put("sourceSection", "chorus");
		params.put("bindingRuleId", "rule-1");
		params.put("layerId", "layer-x");
		params.put("layerBound", "true");
		params.put("dispatchModel", "STEP");

		TimelineAnimationEvent source = new TimelineAnimationEvent(
			"evt-gen",
			5.0,
			0.4,
			"Pulse",
			"stage-a",
			0.9f,
			params
		);

		EventTemplate template = EventTemplate.fromAnimationEvent(source, "Pulse Snapshot");
		Map<String, Object> stored = template.parameters();

		assertEquals(TimelineEventOrigin.MANUAL.name(), stored.get("eventOrigin"));
		assertNull(stored.get(TimelineGenerationMetadataSupport.PARAM_GENERATOR_ID));
		assertNull(stored.get(TimelineGenerationMetadataSupport.PARAM_GENERATION_ID));
		assertNull(stored.get(TimelineGenerationMetadataSupport.PARAM_SECTION_INDEX));
		assertNull(stored.get(TimelineGenerationMetadataSupport.PARAM_PHRASE_INDEX));
		assertNull(stored.get(TimelineGenerationMetadataSupport.PARAM_SOURCE_PLAN_ID));
		assertNull(stored.get("generatedBy"));
		assertNull(stored.get("sourceFeature"));
		assertNull(stored.get("sourceSection"));
		assertNull(stored.get("bindingRuleId"));
		assertNull(stored.get("targetObject"));
		assertNull(stored.get("layerId"));
		assertNull(stored.get("layerBound"));
		assertEquals("STEP", stored.get("dispatchModel"));

		TimelineAnimationEvent applied = template.toTimelineEvent(1.0, "new-target");
		assertEquals(TimelineEventOrigin.MANUAL.name(), applied.getParameters().get("eventOrigin"));
		assertEquals("new-target", applied.getTargetObjectId());
		assertNull(applied.getParameters().get("generatorId"));
	}
}
