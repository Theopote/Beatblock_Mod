package com.beatblock.timeline.project.migration;

import com.google.gson.JsonObject;

/**
 * Creator {@code schemaVersion} 链式迁移步骤（与 legacy {@code version} 命名空间分离）。
 */
interface CreatorSchemaMigration {

	int fromSchema();

	int toSchema();

	String describeStep();

	JsonObject migrate(JsonObject source);
}
