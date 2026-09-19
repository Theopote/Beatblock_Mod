package com.beatblock.timeline;

import org.jspecify.annotations.Nullable;

/**
 * 事件来源粗粒度分类：手工、系统生成、用户改过的生成内容、外部导入。
 * <p>
 * 细粒度归属（smart-automap、ai-director、template:x 等）由
 * {@link com.beatblock.timeline.generation.TimelineGenerationMetadata#generatorId()} 承载。
 * 锁定不是 Origin，见 {@link com.beatblock.timeline.generation.TimelineEventOwnership#PARAM_LOCKED}。
 */
public enum TimelineEventOrigin {
	MANUAL,
	GENERATED,
	USER_EDITED,
	IMPORTED;

	/** @deprecated 使用 {@link #GENERATED}；读取旧项目时 {@link #fromValue(Object)} 仍兼容。 */
	@Deprecated
	public static final TimelineEventOrigin AUTO_GENERATED = GENERATED;

	public boolean isManual() {
		return this == MANUAL;
	}

	public boolean isGenerated() {
		return this == GENERATED;
	}

	public boolean isUserEdited() {
		return this == USER_EDITED;
	}

	public boolean isImported() {
		return this == IMPORTED;
	}

	/** 可被自动生成管线替换的内容（仅纯 {@link #GENERATED}）。 */
	public boolean isReplaceableByGeneration() {
		return this == GENERATED;
	}

	public static TimelineEventOrigin fromValue(@Nullable Object raw) {
		if (raw == null) return MANUAL;
		String s = String.valueOf(raw).trim();
		if (s.isEmpty()) return MANUAL;
		String normalized = s.toUpperCase().replace('-', '_');
		return switch (normalized) {
			case "MANUAL" -> MANUAL;
			case "GENERATED", "AUTO_GENERATED", "AUTO_MAP",
				"AI_GENERATED", "AI", "TEMPLATE", "SCRIPT" -> GENERATED;
			case "USER_EDITED", "EDITED", "GENERATED_EDITED" -> USER_EDITED;
			case "IMPORTED", "OSC", "OSC_IMPORT" -> IMPORTED;
			default -> {
				try {
					yield valueOf(normalized);
				} catch (IllegalArgumentException ex) {
					yield MANUAL;
				}
			}
		};
	}
}
