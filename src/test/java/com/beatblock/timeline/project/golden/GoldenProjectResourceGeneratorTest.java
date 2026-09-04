package com.beatblock.timeline.project.golden;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.project.OscProjectStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 将 {@link GoldenProjectFixtures} 写入 {@code src/test/resources/projects/}。 */
@WithBeatBlockContext
class GoldenProjectResourceGeneratorTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void writeOfficialGoldenProjectsToResources(@TempDir Path tempDir) throws Exception {
		Path resourcesDir = Paths.get("src/test/resources/projects");
		Files.createDirectories(resourcesDir);
		for (var entry : GoldenProjectFixtures.all()) {
			Path target = resourcesDir.resolve(entry.getKey());
			OscProjectStore.save(target, entry.getValue().timeline(), entry.getValue().layers());
			normalizeCommittedProjectPath(target, "projects/" + entry.getKey());
		}
		writeBrokenReferenceProject(resourcesDir);
	}

	private static void writeBrokenReferenceProject(Path resourcesDir) throws Exception {
		Path target = resourcesDir.resolve("broken-reference.osc");
		GoldenProjectContext context = GoldenProjectFixtures.brokenReference();
		OscProjectStore.save(target, context.timeline(), context.layers());
		normalizeCommittedProjectPath(target, "projects/broken-reference.osc");
	}

	private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

	private static void normalizeCommittedProjectPath(Path projectFile, String stablePath) throws Exception {
		String json = Files.readString(projectFile, StandardCharsets.UTF_8);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		root.addProperty("projectPath", stablePath);
		Files.writeString(projectFile, PRETTY_GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	@Test
	void fixturesCompileInMemoryBeforeWriting(@TempDir Path tempDir) throws Exception {
		for (var entry : GoldenProjectFixtures.all()) {
			Path source = tempDir.resolve(entry.getKey());
			Path roundTrip = tempDir.resolve("roundtrip-" + entry.getKey());
			OscProjectStore.save(source, entry.getValue().timeline(), entry.getValue().layers());
			GoldenProjectRegressionHarness.run(
				source, roundTrip, entry.getValue().probeTimesSeconds());
		}
	}
}
