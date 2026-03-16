package com.beatblock.audio.beatmap;

/** 踩点锚点类型。 */
public enum AnchorType {
	/** 踩点 = 方块落地瞬间，系统需提前发射 */
	ARRIVE,
	/** 踩点 = 方块起飞瞬间，系统即时发射 */
	DEPART;

	static AnchorType fromJson(String s) {
		return switch (s.toLowerCase()) {
			case "arrive" -> ARRIVE;
			case "depart" -> DEPART;
			default -> throw new IllegalArgumentException("未知锚点类型: " + s);
		};
	}
}

