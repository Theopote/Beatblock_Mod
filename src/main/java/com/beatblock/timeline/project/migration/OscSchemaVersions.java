package com.beatblock.timeline.project.migration;

/**
 * .osc Creator Alpha 稳定 schema 版本常量。
 * <p>
 * <b>两套版本命名空间（勿混淆）：</b>
 * <ul>
 *   <li>{@code version} — legacy 预发布格式，取值 1–4，<b>已冻结</b>，新工程不再写入</li>
 *   <li>{@code schemaVersion} — Creator 稳定格式，当前为 {@link #CURRENT}，今后只演进此字段</li>
 * </ul>
 * legacy {@code version: 4} 与 {@code schemaVersion: 3} 数字相同但含义不同；
 * 跨命名空间迁移见 {@link LegacyFormatV4ToCreatorSchemaV3Migration}。
 */
public final class OscSchemaVersions {

	/** JSON 根对象上的格式标识。 */
	public static final String FORMAT = "beatblock.osc";

	/** 当前稳定 Creator schema（Creator Alpha）。 */
	public static final int CURRENT = 3;

	/** legacy {@code version} 字段的最高值（冻结，不再新增 legacy 版本）。 */
	public static final int LEGACY_FORMAT_MAX = 4;

	/**
	 * @deprecated 使用 {@link #LEGACY_FORMAT_MAX}；保留别名避免外部误读为 schema 版本。
	 */
	@Deprecated
	public static final int LEGACY_MAX_VERSION = LEGACY_FORMAT_MAX;

	private OscSchemaVersions() {}
}
