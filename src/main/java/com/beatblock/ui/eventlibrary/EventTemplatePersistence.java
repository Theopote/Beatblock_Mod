package com.beatblock.ui.eventlibrary;

import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Disk format for {@code event_templates.json}: schema detection, v0→v1 migration, atomic write.
 */
public final class EventTemplatePersistence {

	public static final int SCHEMA_VERSION = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type PARAM_MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

	public enum LoadStatus {
		/** File missing — empty library is fine. */
		MISSING,
		/** Parsed successfully (possibly migrated from an older schema). */
		OK,
		/** Corrupt / unsupported — must not overwrite the on-disk file. */
		ERROR
	}

	/**
	 * @param sourceSchemaVersion detected on-disk schema (0 = bare array); -1 when missing/error
	 * @param needsRewrite true when memory should be flushed as current envelope (e.g. v0→v1)
	 */
	public record LoadResult(
		LoadStatus status,
		List<EventTemplate> templates,
		int sourceSchemaVersion,
		boolean needsRewrite,
		@Nullable String errorMessage
	) {
		public static LoadResult missing() {
			return new LoadResult(LoadStatus.MISSING, List.of(), -1, false, null);
		}

		public static LoadResult ok(List<EventTemplate> templates, int sourceSchemaVersion, boolean needsRewrite) {
			return new LoadResult(
				LoadStatus.OK,
				List.copyOf(templates != null ? templates : List.of()),
				sourceSchemaVersion,
				needsRewrite,
				null
			);
		}

		public static LoadResult error(String message) {
			return new LoadResult(LoadStatus.ERROR, List.of(), -1, false, message);
		}
	}

	private EventTemplatePersistence() {
	}

	public static LoadResult read(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return LoadResult.missing();
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			return parse(json);
		} catch (Exception e) {
			return LoadResult.error(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
		}
	}

	/** Parse JSON text: bare array = schema 0; object envelope = schemaVersion. */
	public static LoadResult parse(@Nullable String json) {
		if (json == null || json.isBlank()) {
			return LoadResult.error("empty document");
		}
		JsonElement root;
		try {
			root = JsonParser.parseString(json);
		} catch (Exception e) {
			return LoadResult.error(e.getMessage() != null ? e.getMessage() : "invalid JSON");
		}
		if (root == null || root.isJsonNull()) {
			return LoadResult.error("null document");
		}
		if (root.isJsonArray()) {
			List<EventTemplate> templates = parseTemplateArray(root.getAsJsonArray());
			return LoadResult.ok(templates, 0, true);
		}
		if (!root.isJsonObject()) {
			return LoadResult.error("unsupported root type");
		}
		JsonObject object = root.getAsJsonObject();
		if (!object.has("schemaVersion") || object.get("schemaVersion").isJsonNull()) {
			return LoadResult.error("missing schemaVersion");
		}
		int schemaVersion;
		try {
			schemaVersion = object.get("schemaVersion").getAsInt();
		} catch (Exception e) {
			return LoadResult.error("invalid schemaVersion");
		}
		if (schemaVersion > SCHEMA_VERSION) {
			return LoadResult.error("unsupported schemaVersion: " + schemaVersion
				+ " (current " + SCHEMA_VERSION + ")");
		}
		if (schemaVersion < 0) {
			return LoadResult.error("invalid schemaVersion: " + schemaVersion);
		}
		JsonArray array = object.has("templates") && object.get("templates").isJsonArray()
			? object.getAsJsonArray("templates")
			: new JsonArray();
		List<EventTemplate> templates = parseTemplateArray(array);
		boolean needsRewrite = schemaVersion < SCHEMA_VERSION;
		return LoadResult.ok(templates, schemaVersion, needsRewrite);
	}

	public static String serialize(List<EventTemplate> templates) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		JsonArray array = new JsonArray();
		if (templates != null) {
			for (EventTemplate template : templates) {
				if (template == null) continue;
				array.add(toJson(template));
			}
		}
		root.add("templates", array);
		return GSON.toJson(root);
	}

	/**
	 * Write current schema envelope via unique temp file + atomic replace (fallback when needed).
	 */
	public static void writeAtomically(Path path, List<EventTemplate> templates) throws IOException {
		if (path == null) {
			throw new IOException("event template path is null");
		}
		Path abs = path.toAbsolutePath().normalize();
		Path parent = abs.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		String content = serialize(templates);
		Path fileNamePath = abs.getFileName();
		String fileName = fileNamePath != null ? fileNamePath.toString() : "event_templates.json";
		Path temp = null;
		try {
			temp = parent != null
				? Files.createTempFile(parent, fileName + ".", ".tmp")
				: Files.createTempFile(fileName + ".", ".tmp");
			Files.writeString(temp, content, StandardCharsets.UTF_8);
			moveReplacing(temp, abs);
			temp = null;
		} finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				} catch (IOException ignored) {
					// cleanup must not mask primary failure
				}
			}
		}
	}

	static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static List<EventTemplate> parseTemplateArray(JsonArray array) {
		List<EventTemplate> templates = new ArrayList<>();
		if (array == null) {
			return templates;
		}
		for (JsonElement element : array) {
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			EventTemplate template = parseTemplate(element.getAsJsonObject());
			if (template != null) {
				templates.add(template);
			}
		}
		return templates;
	}

	static @Nullable EventTemplate parseTemplate(JsonObject obj) {
		if (obj == null) {
			return null;
		}
		String id = stringOrEmpty(obj, "id");
		String name = stringOrEmpty(obj, "name");
		String animationTypeId = stringOrEmpty(obj, "animationTypeId");
		if (id.isBlank() && name.isBlank() && animationTypeId.isBlank()) {
			return null;
		}
		double duration = obj.has("durationSeconds") && obj.get("durationSeconds").isJsonPrimitive()
			? obj.get("durationSeconds").getAsDouble()
			: 0.5;
		float energy = obj.has("energy") && obj.get("energy").isJsonPrimitive()
			? obj.get("energy").getAsFloat()
			: 0.7f;
		Map<String, Object> params = obj.has("parameters")
			? GSON.fromJson(obj.get("parameters"), PARAM_MAP_TYPE)
			: Map.of();
		if (params == null) {
			params = Map.of();
		}
		Map<String, Object> sanitized = TimelineGenerationMetadataSupport.sanitizeForTemplate(params);
		return new EventTemplate(id, name, animationTypeId, duration, energy, sanitized);
	}

	private static JsonObject toJson(EventTemplate template) {
		JsonObject obj = new JsonObject();
		obj.addProperty("id", template.id());
		obj.addProperty("name", template.name());
		obj.addProperty("animationTypeId", template.animationTypeId());
		obj.addProperty("durationSeconds", template.durationSeconds());
		obj.addProperty("energy", template.energy());
		obj.add("parameters", GSON.toJsonTree(template.parameters()));
		return obj;
	}

	private static String stringOrEmpty(JsonObject obj, String key) {
		return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : "";
	}
}
