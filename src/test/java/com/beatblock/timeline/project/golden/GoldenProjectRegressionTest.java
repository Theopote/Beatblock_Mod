package com.beatblock.timeline.project.golden;

import com.beatblock.testutil.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * 作品级回归：对官方 Golden Project 执行完整 load → validate → compile → playback/seek → save → reload 管线。
 */
class GoldenProjectRegressionTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@TempDir
	Path tempDir;

	@ParameterizedTest(name = "{0}")
	@EnumSource(GoldenProject.class)
	void officialGoldenProjectRoundTripPipeline(GoldenProject project) {
		Duration timeout = project == GoldenProject.STRESS_10K
			? Duration.ofMinutes(3)
			: Duration.ofSeconds(45);

		assertTimeout(timeout, () -> {
			Path source = GoldenProjectRegressionHarness.copyResourceTo(tempDir, project.fileName());
			Path roundTrip = tempDir.resolve("roundtrip-" + project.fileName());

			GoldenProjectRegressionHarness.RoundTripResult result = GoldenProjectRegressionHarness.run(
				source, roundTrip, project.probeTimes());

			assertEquals(
				result.compileFingerprint(),
				result.reloadedCompileFingerprint(),
				() -> "Round-trip fingerprint mismatch for " + project.fileName());

			for (GoldenProjectRegressionHarness.ProbeResult probe : result.probes()) {
				assertEquals(
					probe.play(),
					probe.seek(),
					() -> "playTo vs reconstructAt mismatch at t=" + probe.timeSeconds()
						+ " for " + project.fileName());
			}
		});
	}

	enum GoldenProject {
		MINIMAL("minimal.osc", new double[] {0.0, 15.0, 30.0, 45.0, 60.0}),
		BUILD_DEMO("build-demo.osc", new double[] {0.0, 11.25, 22.5, 33.75, 45.0}),
		THREE_BAND("three-band.osc", new double[] {0.0, 8.0, 16.0, 24.0, 32.0}),
		CAMERA_VFX("camera-vfx.osc", new double[] {0.0, 12.0, 24.0, 36.0, 48.0}),
		MANUAL_PLUS_AUTOMAP("manual-plus-automap.osc", new double[] {0.0, 12.0, 24.0, 36.0, 48.0}),
		CREATOR_ALPHA_SHOWCASE("creator-alpha-showcase.osc", new double[] {0.0, 10.0, 30.0, 50.0, 70.0, 80.0}),
		STRESS_10K("stress-10k.osc", new double[] {0.0, 2500.0, 5000.0, 7500.0, 10_000.0});

		private final String fileName;
		private final double[] probeTimes;

		GoldenProject(String fileName, double[] probeTimes) {
			this.fileName = fileName;
			this.probeTimes = probeTimes;
		}

		String fileName() {
			return fileName;
		}

		double[] probeTimes() {
			return probeTimes;
		}
	}
}
