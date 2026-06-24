package com.beatblock.ui.presenter;

import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.binding.AnimationBindingRule;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import com.beatblock.timeline.TimelineAnimationActionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineBindingEditorPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private TimelineBindingEditorPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		presenter = new TimelineBindingEditorPresenter(
			() -> timeline,
			() -> editor,
			() -> null
		);
	}

	@Test
	void loadEditorListsIncludesDefaultAnimationWhenEngineMissing() {
		var lists = presenter.loadEditorLists(timeline);
		assertEquals(List.of("Pulse"), lists.animationIds());
		assertTrue(lists.sectionFilters().contains(TimelineBindingEditorPresenter.SECTION_ALL));
	}

	@Test
	void sectionFiltersIncludeSectionMarkers() {
		timeline.addMarker(new TimelineMarker(1.0, "SECTION Intro", MarkerType.SECTION));
		var lists = presenter.loadEditorLists(timeline);
		assertTrue(lists.sectionFilters().contains("INTRO"));
	}

	@Test
	void buildUpdatedRuleAppliesFormValues() {
		AnimationBindingRule original = AnimationBindingRule.builder()
			.id("rule-1")
			.name("Old")
			.sourceFeatureKey("kick")
			.animationTypeId("Pulse")
			.actionMode(TimelineAnimationActionMode.ANIMATE)
			.targetObjectId("obj1")
			.spatialMode(SpatialDispatchMode.ALL)
			.build();

		var updated = TimelineBindingEditorPresenter.buildUpdatedRule(original,
			new TimelineBindingEditorPresenter.BindingRuleEditRequest(
				false,
				"New Name",
				"snare",
				"WaveMotion",
				"PLACE",
				2,
				"obj2",
				"ALL",
				0.5f,
				1.1f,
				0.75f,
				0.1f,
				0.9f,
				0.05f,
				Map.of("waveAmplitude", 2.0f)
			));

		assertEquals("New Name", updated.name());
		assertFalse(updated.enabled());
		assertEquals("snare", updated.sourceFeatureKey());
		assertEquals("PLACE", updated.actionMode().name());
		assertEquals(SpatialDispatchMode.RADIAL, updated.spatialMode());
	}

	@Test
	void replaceWithTemplateReturnsFailureWhenEmpty() {
		var outcome = presenter.replaceWithTemplate(timeline, List.of(), 0);
		assertFalse(outcome.success());
		assertNotNull(outcome.message());
	}

	@Test
	void indexOfValueFindsCaseInsensitiveMatch() {
		assertEquals(1, TimelineBindingEditorPresenter.indexOfValue(List.of("A", "Kick"), "kick"));
	}

	@Test
	void extraParamAsDoubleParsesNumbers() {
		assertEquals(2.5, TimelineBindingEditorPresenter.extraParamAsDouble(Map.of("k", 2.5), "k", 1.0), 1e-9);
		assertEquals(1.0, TimelineBindingEditorPresenter.extraParamAsDouble(Map.of(), "missing", 1.0), 1e-9);
	}
}
