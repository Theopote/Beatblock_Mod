package com.beatblock.automap.choreography;

import com.beatblock.timeline.generation.ContentReplacePolicy;

/**
 * 编舞计划编译到 Timeline 时的写入策略（legacy 枚举门面）。
 * <p>
 * 新代码请使用 {@link ContentReplacePolicy}（支持按 {@code generatorId} / {@code generationId} / section 替换）。
 */
public enum ReplaceMode {
	/** 在现有内容后追加。 */
	APPEND,
	/** 仅替换全部 {@code GENERATED}（含未标记 generator 的 legacy 内容）。 */
	REPLACE_GENERATED,
	/** 清空整条轨道后写入（含手工内容）。 */
	REPLACE_ALL;

	public ContentReplacePolicy toPolicy() {
		return switch (this) {
			case APPEND -> ContentReplacePolicy.append();
			case REPLACE_GENERATED -> ContentReplacePolicy.replaceGenerated();
			case REPLACE_ALL -> ContentReplacePolicy.replaceAll();
		};
	}

	public static ReplaceMode fromPolicy(ContentReplacePolicy policy) {
		if (policy instanceof ContentReplacePolicy.Append) return APPEND;
		if (policy instanceof ContentReplacePolicy.ReplaceAll) return REPLACE_ALL;
		if (policy instanceof ContentReplacePolicy.ReplaceGenerated) return REPLACE_GENERATED;
		return REPLACE_GENERATED;
	}
}
