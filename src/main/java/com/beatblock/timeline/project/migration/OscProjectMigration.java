package com.beatblock.timeline.project.migration;

import com.beatblock.BeatBlock;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * .osc 工程格式链式迁移入口。
 * <p>
 * 加载时先将任意受支持的 legacy {@code version}（1–4，已冻结）归一化为当前
 * {@link OscSchemaVersions#CURRENT} {@code schemaVersion}，再由
 * {@link com.beatblock.timeline.project.OscProjectStore} 解析字段。
 * <p>
 * Legacy 链：{@code version} 1→2→3→4，最后一步
 * {@link LegacyFormatV4ToCreatorSchemaV3Migration} 跨命名空间写入 {@code schemaVersion}。
 */
public final class OscProjectMigration {

	private static final List<ProjectMigration> LEGACY_FORMAT_CHAIN = List.of(
		new LegacyFormatV1ToV2Migration(),
		new LegacyFormatV2ToV3Migration(),
		new LegacyFormatV3ToV4Migration(),
		new LegacyFormatV4ToCreatorSchemaV3Migration()
	);

	private static final Map<Integer, ProjectMigration> LEGACY_BY_FROM = indexByFrom(LEGACY_FORMAT_CHAIN);

	private OscProjectMigration() {}

	/**
	 * 将 JSON 根对象迁移到当前 schema。已是最新 schema 时返回深拷贝。
	 *
	 * @throws IOException 版本不受支持或迁移链断裂
	 */
	public static JsonObject migrateToCurrent(JsonObject source) throws IOException {
		if (source == null) {
			throw new IOException("打开失败：.osc 根对象为空");
		}

		if (source.has("schemaVersion") && !source.get("schemaVersion").isJsonNull()) {
			int schemaVersion = readInt(source, "schemaVersion", 0);
			if (schemaVersion > OscSchemaVersions.CURRENT) {
				throw unsupportedVersion(schemaVersion, OscSchemaVersions.CURRENT, true);
			}
			if (schemaVersion == OscSchemaVersions.CURRENT) {
				return source.deepCopy();
			}
			throw new IOException("不支持的 .osc schemaVersion: " + schemaVersion
				+ "（当前支持 <= " + OscSchemaVersions.CURRENT + "，且缺少对应迁移）");
		}

		JsonObject current = source.deepCopy();
		while (!current.has("schemaVersion")) {
			int legacyVersion = readLegacyVersion(current);
			if (legacyVersion > OscSchemaVersions.LEGACY_FORMAT_MAX) {
				throw unsupportedVersion(legacyVersion, OscSchemaVersions.LEGACY_FORMAT_MAX, false);
			}
			ProjectMigration step = LEGACY_BY_FROM.get(legacyVersion);
			if (step == null) {
				throw new IOException("缺少从 legacy format version " + legacyVersion + " 的迁移步骤");
			}
			BeatBlock.LOGGER.info("Migrating .osc: {}", step.describeStep());
			current = step.migrate(current);
		}
		return current;
	}

	static int readLegacyVersion(JsonObject root) {
		if (root == null || !root.has("version") || root.get("version").isJsonNull()) {
			return 1;
		}
		return readInt(root, "version", 1);
	}

	private static int readInt(JsonObject obj, String key, int def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return def;
		}
		try {
			return obj.get(key).getAsInt();
		} catch (RuntimeException e) {
			return def;
		}
	}

	private static IOException unsupportedVersion(int found, int maxSupported, boolean schemaField) {
		String field = schemaField ? "schemaVersion" : "version";
		return new IOException("不支持的 .osc " + field + ": " + found
			+ " (当前支持 <= " + maxSupported + ")");
	}

	private static Map<Integer, ProjectMigration> indexByFrom(List<ProjectMigration> chain) {
		Map<Integer, ProjectMigration> map = new LinkedHashMap<>();
		for (ProjectMigration migration : chain) {
			map.put(migration.fromVersion(), migration);
		}
		return Map.copyOf(map);
	}
}
