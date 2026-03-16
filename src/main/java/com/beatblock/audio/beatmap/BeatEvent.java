package com.beatblock.audio.beatmap;

/**
 * 单个踩点事件，对应 JSON beats 数组中的一个元素。
 */
public record BeatEvent(
	long          timeMs,      // 踩点绝对时间（毫秒）
	FrequencyBand band,        // 频段
	float         energy,      // 能量强度 0~1
	AnchorType    anchor,      // 锚点类型
	int           beatIndex,   // 全曲节拍序号
	int           barIndex,    // 小节序号
	int           beatInBar    // 拍内位置 0~3
) implements Comparable<BeatEvent> {

	@Override
	public int compareTo(BeatEvent other) {
		return Long.compare(this.timeMs, other.timeMs);
	}
}

