package com.beatblock.automap.choreography;

/**
 * 编舞计划编译到 Timeline 时的写入策略。
 */
public enum ReplaceMode {
	/** 在现有内容后追加。 */
	APPEND,
	/** 仅替换自动生成内容，保留手工编辑。 */
	REPLACE_GENERATED,
	/** 清空整条轨道后写入（含手工内容）。 */
	REPLACE_ALL
}
