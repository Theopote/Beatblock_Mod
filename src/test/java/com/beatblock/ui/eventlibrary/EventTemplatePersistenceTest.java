package com.beatblock.ui.eventlibrary;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.timeline.TimelineEventOrigin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class EventTemplatePersistenceTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		EventTemplateStore.resetForTests();
		System.setProperty("beatblock.test.configDir", tempDir.toAbsolutePath().toString());
	}

	@AfterEach
	void tearDown() {
		EventTemplateStore.resetForTests();
	}

	@Test
	void parseBareArrayAsSchemaZeroAndSanitizeProvenance() {
		String v0 = """
			[
			  {
			    "id": "tpl-1",
			    "name": "Pulse Snap",
			    "animationTypeId": "Pulse",
			    "durationSeconds": 0.35,
			    "energy": 0.8,
			    "parameters": {
			      "actionMode": "ANIMATE",
			      "animationType": "Pulse",
			      "eventOrigin": "GENERATED",
			      "generatorId": "smart-automap",
			      "generationId": "gen-old",
			      "generatedBy": "AutoMap",
			      "targetObject": "stage-a",
			      "dispatchModel": "STEP"
			    }
			  }
			]
			""";

		var result = EventTemplatePersistence.parse(v0);
		assertEquals(EventTemplatePersistence.LoadStatus.OK, result.status());
		assertEquals(0, result.sourceSchemaVersion());
		assertTrue(result.needsRewrite());
		assertEquals(1, result.templates().size());

		EventTemplate template = result.templates().getFirst();
		assertEquals("tpl-1", template.id());
		assertEquals(TimelineEventOrigin.MANUAL.name(), template.parameters().get("eventOrigin"));
		assertNull(template.parameters().get("generatorId"));
		assertNull(template.parameters().get("generatedBy"));
		assertNull(template.parameters().get("targetObject"));
		assertEquals("STEP", template.parameters().get("dispatchModel"));
	}

	@Test
	void parseV1Envelope() {
		String v1 = """
			{
			  "schemaVersion": 1,
			  "templates": [
			    {
			      "id": "a",
			      "name": "A",
			      "animationTypeId": "Pulse",
			      "durationSeconds": 0.5,
			      "energy": 0.7,
			      "parameters": { "actionMode": "ANIMATE" }
			    }
			  ]
			}
			""";
		var result = EventTemplatePersistence.parse(v1);
		assertEquals(EventTemplatePersistence.LoadStatus.OK, result.status());
		assertEquals(1, result.sourceSchemaVersion());
		assertFalse(result.needsRewrite());
		assertEquals(1, result.templates().size());
	}

	@Test
	void parseFutureSchemaIsError() {
		var result = EventTemplatePersistence.parse("{\"schemaVersion\":99,\"templates\":[]}");
		assertEquals(EventTemplatePersistence.LoadStatus.ERROR, result.status());
		assertTrue(result.errorMessage().contains("99"));
	}

	@Test
	void parseCorruptJsonIsError() {
		var result = EventTemplatePersistence.parse("{not-json");
		assertEquals(EventTemplatePersistence.LoadStatus.ERROR, result.status());
	}

	@Test
	void serializeWritesSchemaEnvelope() {
		EventTemplate template = new EventTemplate(
			"id-1", "Name", "Pulse", 0.4, 0.5f, Map.of("actionMode", "ANIMATE"));
		String json = EventTemplatePersistence.serialize(List.of(template));
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		assertEquals(1, root.get("schemaVersion").getAsInt());
		assertEquals(1, root.getAsJsonArray("templates").size());
	}

	@Test
	void storeMigratesV0ArrayToV1OnLoad() throws Exception {
		Path file = tempDir.resolve("event_templates.json");
		Files.writeString(file, """
			[{"id":"legacy","name":"Legacy","animationTypeId":"Pulse","durationSeconds":0.3,"energy":0.6,"parameters":{}}]
			""", StandardCharsets.UTF_8);

		assertEquals(EventTemplateStore.StoreState.READY, EventTemplateStore.state());
		assertEquals(1, EventTemplateStore.all().size());
		assertEquals("legacy", EventTemplateStore.all().getFirst().id());

		String rewritten = Files.readString(file, StandardCharsets.UTF_8);
		JsonObject root = JsonParser.parseString(rewritten).getAsJsonObject();
		assertEquals(EventTemplatePersistence.SCHEMA_VERSION, root.get("schemaVersion").getAsInt());
		assertEquals(1, root.getAsJsonArray("templates").size());
	}

	@Test
	void loadErrorDoesNotOverwriteCorruptFileAndBlocksSave() throws Exception {
		Path file = tempDir.resolve("event_templates.json");
		String corrupt = "{this is not valid json";
		Files.writeString(file, corrupt, StandardCharsets.UTF_8);

		assertEquals(EventTemplateStore.StoreState.LOAD_ERROR, EventTemplateStore.state());
		assertTrue(EventTemplateStore.all().isEmpty());

		EventTemplate template = new EventTemplate(
			"new", "New", "Pulse", 0.3, 0.5f, Map.of("actionMode", "ANIMATE"));
		assertFalse(EventTemplateStore.add(template));
		assertFalse(EventTemplateStore.remove("anything"));

		assertEquals(corrupt, Files.readString(file, StandardCharsets.UTF_8));
	}

	@Test
	void missingFileStartsReadyAndAtomicSaveWritesV1() throws Exception {
		assertEquals(EventTemplateStore.StoreState.READY, EventTemplateStore.state());
		assertTrue(EventTemplateStore.add(new EventTemplate(
			"fresh", "Fresh", "Pulse", 0.35, 0.8f, Map.of("actionMode", "ANIMATE"))));

		Path file = tempDir.resolve("event_templates.json");
		assertTrue(Files.isRegularFile(file));
		JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(1, root.get("schemaVersion").getAsInt());
		assertEquals(1, root.getAsJsonArray("templates").size());

		long tmpCount = Files.list(tempDir)
			.filter(p -> p.getFileName().toString().endsWith(".tmp"))
			.count();
		assertEquals(0, tmpCount);
	}
}
