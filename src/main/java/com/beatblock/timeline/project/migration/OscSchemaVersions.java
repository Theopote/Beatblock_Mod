package com.beatblock.timeline.project.migration;

/**
 * .osc Creator Alpha 稳定 schema 版本常量。
 * <p>
 * 历史工程使用 legacy {@code version} 字段（1–4）；新工程统一写入 {@code schemaVersion}。
 */
public final class OscSchemaVersions {

	/** JSON 根对象上的格式标识。 */
	public static final String FORMAT = "beatblock.osc";

	/** 当前稳定 schema（Creator Alpha）。 */
	public static final int CURRENT = 3;

	/** 可通过链式迁移到达的最高 legacy {@code version} 值。 */
	public static final int LEGACY_MAX_VERSION = 4;

	private OscSchemaVersions() {}
}
