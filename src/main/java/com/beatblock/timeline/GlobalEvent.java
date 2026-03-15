package com.beatblock.timeline;

/**
 * 全局事件轨道上的单条事件（舞台切换、灯光、特殊效果等）。
 */
public final class GlobalEvent {

	private final double timeSeconds;
	private final GlobalEventType type;
	private final String name;

	public GlobalEvent(double timeSeconds, GlobalEventType type, String name) {
		this.timeSeconds = Math.max(0, timeSeconds);
		this.type = type != null ? type : GlobalEventType.SPECIAL;
		this.name = name != null ? name : "";
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}

	public GlobalEventType getType() {
		return type;
	}

	public String getName() {
		return name;
	}
}
