package com.beatblock.timeline.project.migration;

import com.google.gson.JsonObject;

/**
 * 单步 .osc 工程格式迁移。
 * <p>
 * 存在两套版本命名空间（见 {@link OscSchemaVersions}）：
 * <ul>
 *   <li>legacy {@code version}（1–4，已冻结）</li>
 *   <li>Creator {@code schemaVersion}（当前稳定值，长期唯一演进字段）</li>
 * </ul>
 * 加载时先沿 legacy 链升级，最后一步跨命名空间写入 {@code schemaVersion}。
 */
public interface ProjectMigration {

	/** 源 legacy {@code version}（仅用于链式索引；最后一步的 {@link #toVersion()} 为 schemaVersion）。 */
	int fromVersion();

	/**
	 * 目标版本号：legacy 链中间步骤为下一 legacy {@code version}；
	 * 跨命名空间步骤（{@link LegacyFormatV4ToCreatorSchemaV3Migration}）为 {@link OscSchemaVersions#CURRENT}。
	 */
	int toVersion();

	/** 日志 / 文档用的人类可读步骤描述。 */
	default String describeStep() {
		return "legacy format version " + fromVersion() + " → " + toVersion();
	}

	/** 返回迁移后的新根对象（可修改副本，不应修改入参）。 */
	JsonObject migrate(JsonObject source);
}
