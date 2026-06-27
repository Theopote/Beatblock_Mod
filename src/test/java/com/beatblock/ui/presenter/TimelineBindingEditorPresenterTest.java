package com.beatblock.ui.presenter;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.binding.AnimationBindingEngine;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
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
		assertEquals("WaveMotion", updated.animationTypeId());
		assertEquals("obj2", updated.targetObjectId());
		assertEquals("PLACE", updated.actionMode().name());
		assertEquals(SpatialDispatchMode.RADIAL, updated.spatialMode());
		assertEquals("", updated.sectionFilter());
		assertEquals(0.5f, updated.energyThreshold(), 1e-6f);
		assertEquals(0.75f, updated.durationSeconds(), 1e-9);
		assertEquals(2.0f, updated.extraParams().get("waveAmplitude"));
	}

	@Test
	void buildUpdatedRuleNormalizesSectionFilter() {
		AnimationBindingRule original = AnimationBindingRule.builder()
			.name("Rule")
			.sourceFeatureKey("kick")
			.animationTypeId("Pulse")
			.targetObjectId("obj1")
			.build();

		var updated = TimelineBindingEditorPresenter.buildUpdatedRule(original,
			new TimelineBindingEditorPresenter.BindingRuleEditRequest(
				true,
				"Rule",
				"kick",
				"Pulse",
				"ANIMATE",
				0,
				"obj1",
				"DROP",
				0.2f,
				1.0f,
				0.4f,
				0.0f,
				1.0f,
				0.0f,
				Map.of()
			));

		assertEquals("drop", updated.sectionFilter());
	}

	@Test
	void applyToAutoTrackGeneratesEventsFromSavedRules() {
		timeline.addFeatureEvent("kick", new FeatureEvent(1.0, 0.8f));
		AnimationBindingEngine.saveRules(timeline, List.of(
			AnimationBindingRule.builder()
				.sourceFeatureKey("kick")
				.animationTypeId("Pulse")
				.targetObjectId("stage-a")
				.energyThreshold(0.2f)
				.probability(1.0f)
				.build()
		));

		var outcome = presenter.applyToAutoTrack();

		assertTrue(outcome.success());
		assertEquals(1, outcome.count());
		assertEquals(1, timeline.getAutoAnimationEvents().size());
	}

	@Test
	void removeRuleIgnoresInvalidIndex() {
		List<AnimationBindingRule> rules = List.of(
			AnimationBindingRule.builder().name("A").sourceFeatureKey("kick").targetObjectId("x").build()
		);

		assertSame(rules, presenter.removeRule(rules, -1));
		assertSame(rules, presenter.removeRule(rules, 1));
		assertEquals(0, presenter.removeRule(rules, 0).size());
	}

	@Test
	void loadRulesReturnsEmptyWhenTimelineMissing() {
		assertTrue(presenter.loadRules(null).isEmpty());
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
