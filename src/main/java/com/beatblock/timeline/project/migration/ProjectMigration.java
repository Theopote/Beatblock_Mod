package com.beatblock.timeline.project.migration;

import com.google.gson.JsonObject;

/**
 * 单步 .osc 工程格式迁移：将 {@code fromVersion} 的 JSON 根对象变换为 {@code toVersion}。
 * <p>
 * 多个迁移按版本号链式执行，例如 v1→v2→v3→v4→schemaVersion 3。
 */
public interface ProjectMigration {

	/** 源版本（legacy {@code version} 字段或中间 schema 版本）。 */
	int fromVersion();

	/** 目标版本。 */
	int toVersion();

	/** 返回迁移后的新根对象（可修改副本，不应修改入参）。 */
	JsonObject migrate(JsonObject source);
}
