package com.beatblock.timeline.project.golden;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.playback.CompilePolicy;
import com.beatblock.timeline.playback.PerformanceCheckController;
import com.beatblock.timeline.playback.TimelineAutoRepair;
import com.beatblock.timeline.playback.TimelineCompilationException;
import com.beatblock.timeline.playback.TimelineCompiler;
import com.beatblock.timeline.playback.TimelineDiagnostic;
import com.beatblock.timeline.playback.TimelineValidationReport;
import com.beatblock.timeline.playback.TimelineValidator;
import com.beatblock.timeline.project.OscProjectStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：broken-reference 工程在 Validator / CompilePolicy / Performance Check / AutoRepair 上行为一致。
 */
class BrokenReferenceGoldenProjectTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@AfterEach
	void clearPerformanceCheck() {
		PerformanceCheckController.clear();
	}

	@TempDir
	Path tempDir;

	@Test
	void reportsExpectedBrokenReferencesConsistentlyAcrossValidationPipeline() throws Exception {
		LoadedProject loaded = loadBrokenReference(tempDir);
		BlockAnimationEngine engine = GoldenProjectRegressionHarness.engineFromLayersOnly(loaded.layers());

		TimelineValidationReport validatorReport = TimelineValidator.validate(
			loaded.timeline(), engine, loaded.layers());
		assertTrue(validatorReport.hasErrors());
		assertTrue(validatorReport.hasWarnings());
		assertRuleIds(validatorReport,
			TimelineValidator.RULE_MISSING_STAGE_OBJECT,
			TimelineValidator.RULE_MISSING_ANIMATION_PRESET,
			TimelineValidator.RULE_MISSING_BUILD_LAYER,
			TimelineValidator.RULE_MISSING_AUDIO,
			TimelineValidator.RULE_MISSING_CAMERA_LOOK_AT);

		PerformanceCheckController.clear();
		TimelineValidationReport performanceReport = PerformanceCheckController.checkOnly(
			loaded.timeline(), engine, loaded.layers());
		assertSameRuleIds(validatorReport, performanceReport);

		assertThrows(TimelineCompilationException.class,
			() -> TimelineCompiler.compile(loaded.timeline(), engine, loaded.layers(), CompilePolicy.STRICT));

		var skipResult = TimelineCompiler.compile(
			loaded.timeline(), engine, loaded.layers(), CompilePolicy.SKIP_INVALID_EVENTS);
		assertTrue(skipResult.report().hasErrors());
		assertFalse(skipResult.skippedEventIds().isEmpty() && skipResult.skippedLocations().isEmpty(),
			"expected at least one skipped invalid event");

		for (TimelineDiagnostic diagnostic : validatorReport.problems()) {
			TimelineAutoRepair.RepairDisposition expected = expectedDisposition(diagnostic.ruleId());
			if (expected == null) {
				continue;
			}
			assertEquals(
				expected,
				TimelineAutoRepair.disposition(diagnostic),
				() -> "unexpected repair disposition for " + diagnostic.ruleId());
			if (expected == TimelineAutoRepair.RepairDisposition.SAFE_AUTOMATIC) {
				assertTrue(TimelineAutoRepair.canSafelyRepair(diagnostic),
					() -> diagnostic.ruleId() + " should be safely auto-repaired");
			} else {
				assertFalse(TimelineAutoRepair.canSafelyRepair(diagnostic),
					() -> diagnostic.ruleId() + " should not be safely auto-repaired");
			}
		}

		CommandManager commands = new CommandManager();
		TimelineAutoRepair.RepairResult repairResult = TimelineAutoRepair.apply(
			loaded.timeline(), validatorReport, engine, commands);
		assertTrue(repairResult.repairedEventIds().isEmpty());
		assertTrue(repairResult.repairedLocations().isEmpty());
		assertFalse(repairResult.unresolved().isEmpty());
		assertEquals(0, commands.undoCount());
		assertEquals(0, PerformanceCheckController.safelyRepairableCount());
	}

	@Test
	void brokenReferenceRoundTripsThroughOscPersistence() throws Exception {
		GoldenProjectContext context = GoldenProjectFixtures.brokenReference();
		Path saved = tempDir.resolve("broken-reference.osc");
		OscProjectStore.save(saved, context.timeline(), context.layers());

		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());
		Timeline timeline = Timeline.createDefault();
		OscProjectStore.load(saved, layers, timeline);
		BlockAnimationEngine engine = GoldenProjectRegressionHarness.engineFromLayersOnly(layers);

		TimelineValidationReport report = TimelineValidator.validate(timeline, engine, layers);
		assertTrue(report.hasErrors());
		assertRuleIds(report,
			TimelineValidator.RULE_MISSING_STAGE_OBJECT,
			TimelineValidator.RULE_MISSING_ANIMATION_PRESET,
			TimelineValidator.RULE_MISSING_BUILD_LAYER,
			TimelineValidator.RULE_MISSING_AUDIO);
	}

	private static LoadedProject loadBrokenReference(Path tempDir) throws Exception {
		Path source = GoldenProjectRegressionHarness.copyResourceTo(tempDir, "broken-reference.osc");
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());
		Timeline timeline = Timeline.createDefault();
		OscProjectStore.load(source, layers, timeline);
		return new LoadedProject(timeline, layers);
	}

	private static void assertRuleIds(TimelineValidationReport report, String... expectedRuleIds) {
		Set<String> actual = report.problems().stream()
			.map(TimelineDiagnostic::ruleId)
			.collect(Collectors.toSet());
		for (String ruleId : expectedRuleIds) {
			assertTrue(actual.contains(ruleId), () -> "missing diagnostic rule: " + ruleId + " in " + actual);
		}
	}

	private static void assertSameRuleIds(TimelineValidationReport left, TimelineValidationReport right) {
		Set<String> leftRules = left.problems().stream().map(TimelineDiagnostic::ruleId).collect(Collectors.toSet());
		Set<String> rightRules = right.problems().stream().map(TimelineDiagnostic::ruleId).collect(Collectors.toSet());
		assertEquals(leftRules, rightRules);
	}

	private static TimelineAutoRepair.RepairDisposition expectedDisposition(String ruleId) {
		return switch (ruleId) {
			case TimelineValidator.RULE_MISSING_STAGE_OBJECT,
				TimelineValidator.RULE_MISSING_ANIMATION_PRESET,
				TimelineValidator.RULE_MISSING_BUILD_LAYER,
				TimelineValidator.RULE_MISSING_AUDIO,
				TimelineValidator.RULE_AUDIO_FILE_MISSING -> TimelineAutoRepair.RepairDisposition.REQUIRES_USER_INPUT;
			case TimelineValidator.RULE_MISSING_CAMERA_SUBJECT,
				TimelineValidator.RULE_MISSING_CAMERA_LOOK_AT,
				TimelineValidator.RULE_MISSING_CAMERA_BUILD_LAYER,
				TimelineValidator.RULE_INVALID_CAMERA_FRAMING,
				TimelineValidator.RULE_UNSUPPORTED_CAMERA_TRANSITION -> TimelineAutoRepair.RepairDisposition.NOT_REPAIRABLE;
			default -> null;
		};
	}

	private record LoadedProject(Timeline timeline, BuildLayerManager layers) {}
}
