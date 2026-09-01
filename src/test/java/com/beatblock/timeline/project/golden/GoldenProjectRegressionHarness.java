package com.beatblock.timeline.project.golden;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.playback.CompiledProgramFingerprint;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.PlaybackStateDigest;
import com.beatblock.timeline.playback.TimelineCompilationException;
import com.beatblock.timeline.playback.TimelineCompiler;
import com.beatblock.timeline.playback.TimelineDiagnostic;
import com.beatblock.timeline.playback.TimelineValidationReport;
import com.beatblock.timeline.playback.TimelineValidator;
import com.beatblock.timeline.project.OscProjectStore;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 作品级回归管线：
 * load → validate → compile → playback/seek → save → reload → compile → fingerprint compare
 */
public final class GoldenProjectRegressionHarness {

	public record ProbeResult(double timeSeconds, PlaybackStateDigest play, PlaybackStateDigest seek) {}

	public record RoundTripResult(
		TimelineValidationReport validationReport,
		String compileFingerprint,
		String reloadedCompileFingerprint,
		List<ProbeResult> probes
	) {}

	private GoldenProjectRegressionHarness() {}

	public static RoundTripResult run(Path sourceProject, Path roundTripTarget, double... probeTimesSeconds)
		throws IOException {
		LoadedProject loaded = loadProject(sourceProject);
		BlockAnimationEngine engine = createEngine(loaded.layers());
		registerReferencedStages(loaded.timeline(), engine);

		TimelineValidationReport report = TimelineValidator.validate(loaded.timeline(), engine, loaded.layers());
		if (report.hasErrors()) {
			throw new IOException("Golden project validation failed:\n" + formatProblems(report));
		}

		CompiledTimelineSnapshot compiled = compileOrThrow(loaded.timeline(), engine, loaded.layers());
		String compileFingerprint = CompiledProgramFingerprint.compute(compiled);

		List<ProbeResult> probes = probePlayback(compiled, probeTimesSeconds);

		OscProjectStore.save(roundTripTarget, loaded.timeline(), loaded.layers());

		LoadedProject reloaded = loadProject(roundTripTarget);
		BlockAnimationEngine reloadedEngine = createEngine(reloaded.layers());
		registerReferencedStages(reloaded.timeline(), reloadedEngine);

		TimelineValidationReport reloadedReport = TimelineValidator.validate(
			reloaded.timeline(), reloadedEngine, reloaded.layers());
		if (reloadedReport.hasErrors()) {
			throw new IOException("Reloaded golden project validation failed:\n" + formatProblems(reloadedReport));
		}

		CompiledTimelineSnapshot recompiled = compileOrThrow(reloaded.timeline(), reloadedEngine, reloaded.layers());
		String reloadedCompileFingerprint = CompiledProgramFingerprint.compute(recompiled);

		return new RoundTripResult(report, compileFingerprint, reloadedCompileFingerprint, probes);
	}

	public static Path copyResourceTo(Path targetDir, String resourceName) throws IOException {
		String resourcePath = "/projects/" + resourceName;
		try (var in = GoldenProjectRegressionHarness.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				throw new IOException("Missing classpath resource: " + resourcePath);
			}
			Files.createDirectories(targetDir);
			Path target = targetDir.resolve(resourceName);
			Files.copy(in, target);
			return target;
		}
	}

	private static LoadedProject loadProject(Path projectFile) throws IOException {
		BuildLayerManager layers = new BuildLayerManager(new StageObjectSystem());
		Timeline timeline = Timeline.createDefault();
		OscProjectStore.load(projectFile, layers, timeline);
		return new LoadedProject(timeline, layers);
	}

	private static CompiledTimelineSnapshot compileOrThrow(
		Timeline timeline,
		BlockAnimationEngine engine,
		BuildLayerManager layers
	) {
		try {
			return TimelineCompiler.compile(timeline, engine, layers);
		} catch (TimelineCompilationException error) {
			TimelineValidationReport report = error.report();
			String details = report != null ? formatProblems(report) : error.getMessage();
			throw new IllegalStateException("Golden project compile failed:\n" + details, error);
		}
	}

	private static List<ProbeResult> probePlayback(CompiledTimelineSnapshot compiled, double... probeTimesSeconds) {
		double[] probes = probeTimesSeconds != null && probeTimesSeconds.length > 0
			? probeTimesSeconds
			: defaultProbeTimes(compiled.durationSeconds());
		List<ProbeResult> results = new ArrayList<>(probes.length);
		for (double probe : probes) {
			PlaybackStateDigest play = PlaybackStateDigest.playTo(compiled, probe);
			PlaybackStateDigest seek = PlaybackStateDigest.reconstructAt(compiled, probe);
			results.add(new ProbeResult(probe, play, seek));
		}
		return results;
	}

	private static BlockAnimationEngine createEngine(BuildLayerManager layers) {
		BlockAnimationEngine engine = new BlockAnimationEngine();
		if (layers == null) {
			return engine;
		}
		StageObjectSystem stages = engine.getStageObjectSystem();
		for (BuildLayer layer : layers.getAll()) {
			stages.register(layer.getStageObject());
		}
		return engine;
	}

	private static void registerReferencedStages(Timeline timeline, BlockAnimationEngine engine) {
		if (timeline == null || engine == null) {
			return;
		}
		StageObjectSystem stages = engine.getStageObjectSystem();
		Set<String> seen = new HashSet<>();
		for (TimelineAnimationEvent event : timeline.getStageEvents()) {
			if (event == null) {
				continue;
			}
			String targetId = event.getTargetObjectId();
			if (targetId == null || targetId.isBlank() || !seen.add(targetId) || stages.get(targetId) != null) {
				continue;
			}
			stages.register(StageObjectSystem.fromBlocks(targetId, targetId, List.of(new BlockPos(0, 64, 0))));
		}
	}

	private static double[] defaultProbeTimes(double durationSeconds) {
		double duration = Math.max(0.0, durationSeconds);
		if (duration <= 0.0) {
			return new double[] {0.0, 15.0, 30.0};
		}
		return new double[] {0.0, duration * 0.25, duration * 0.5, duration * 0.75, duration};
	}

	private static String formatProblems(TimelineValidationReport report) {
		return report.problems().stream()
			.map(GoldenProjectRegressionHarness::formatDiagnostic)
			.collect(Collectors.joining(System.lineSeparator()));
	}

	private static String formatDiagnostic(TimelineDiagnostic diagnostic) {
		return diagnostic.severity() + " [" + diagnostic.ruleId() + "] " + diagnostic.message();
	}

	private record LoadedProject(Timeline timeline, BuildLayerManager layers) {}
}
