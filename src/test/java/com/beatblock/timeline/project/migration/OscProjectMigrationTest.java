package com.beatblock.timeline.project.migration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OscProjectMigrationTest {

	@Test
	void migratesLegacyV1ThroughChainToSchema3() throws Exception {
		JsonObject source = JsonParser.parseString("""
			{
			  "projectId": "legacy-1",
			  "timelineName": "Old",
			  "audioPath": "/a.wav"
			}
			""").getAsJsonObject();

		JsonObject migrated = OscProjectMigration.migrateToCurrent(source);

		assertEquals(OscSchemaVersions.FORMAT, migrated.get("format").getAsString());
		assertEquals(OscSchemaVersions.CURRENT, migrated.get("schemaVersion").getAsInt());
		assertFalse(migrated.has("version"));
		assertTrue(migrated.has("buildLayers"));
		assertTrue(migrated.has("markers"));
		assertTrue(migrated.has("animationTracks"));
		assertEquals("legacy-1", migrated.get("projectId").getAsString());
	}

	@Test
	void migratesLegacyV2WithBuildLayers() throws Exception {
		JsonObject source = JsonParser.parseString("""
			{
			  "version": 2,
			  "projectId": "v2",
			  "buildLayers": [{"id": "layer-1"}]
			}
			""").getAsJsonObject();

		JsonObject migrated = OscProjectMigration.migrateToCurrent(source);

		assertEquals(OscSchemaVersions.CURRENT, migrated.get("schemaVersion").getAsInt());
		assertTrue(migrated.has("markers"));
		assertTrue(migrated.has("buildLayerGroups"));
	}

	@Test
	void migratesLegacyV3PreservesMarkersAndTracks() throws Exception {
		JsonObject source = JsonParser.parseString("""
			{
			  "version": 3,
			  "projectId": "v3",
			  "markers": [{"id": "m1", "timeSeconds": 1.0, "name": "A", "type": "DROP"}],
			  "animationTracks": []
			}
			""").getAsJsonObject();

		JsonObject migrated = OscProjectMigration.migrateToCurrent(source);

		assertEquals(OscSchemaVersions.CURRENT, migrated.get("schemaVersion").getAsInt());
		assertEquals(1, migrated.getAsJsonArray("markers").size());
	}

	@Test
	void migratesLegacyV4WithChoreography() throws Exception {
		JsonObject source = JsonParser.parseString("""
			{
			  "version": 4,
			  "projectId": "v4",
			  "choreography": {"sections": []}
			}
			""").getAsJsonObject();

		JsonObject migrated = OscProjectMigration.migrateToCurrent(source);

		assertEquals(OscSchemaVersions.CURRENT, migrated.get("schemaVersion").getAsInt());
		assertTrue(migrated.has("choreography"));
	}

	@Test
	void schema3PassesThroughUnchanged() throws Exception {
		JsonObject source = JsonParser.parseString("""
			{
			  "format": "beatblock.osc",
			  "schemaVersion": 3,
			  "projectId": "current"
			}
			""").getAsJsonObject();

		JsonObject migrated = OscProjectMigration.migrateToCurrent(source);

		assertEquals("current", migrated.get("projectId").getAsString());
		assertEquals(3, migrated.get("schemaVersion").getAsInt());
	}

	@Test
	void rejectsUnsupportedLegacyVersion() {
		JsonObject source = JsonParser.parseString("""
			{"version": 99, "projectId": "x"}
			""").getAsJsonObject();

		assertThrows(Exception.class, () -> OscProjectMigration.migrateToCurrent(source));
	}

	@Test
	void rejectsUnsupportedFutureSchemaVersion() {
		JsonObject source = JsonParser.parseString("""
			{"schemaVersion": 99, "projectId": "x"}
			""").getAsJsonObject();

		assertThrows(Exception.class, () -> OscProjectMigration.migrateToCurrent(source));
	}
}
