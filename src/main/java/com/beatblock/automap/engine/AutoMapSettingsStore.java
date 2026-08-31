package com.beatblock.automap.engine;

/**
 * 会话级 Smart Auto Map 设置（目标映射、minGap 等），供设置弹窗与时间轴工具栏共用。
 */
public final class AutoMapSettingsStore {

	private static final AutoMapSettings CURRENT = new AutoMapSettings();

	private AutoMapSettingsStore() {}

	public static AutoMapSettings current() {
		return CURRENT;
	}

	/** 测试专用：恢复默认设置。 */
	public static void resetForTests() {
		AutoMapSettings fresh = new AutoMapSettings();
		CURRENT.setStyle(fresh.getStyle());
		CURRENT.setComplexity(fresh.getComplexity());
		CURRENT.setCameraEnabled(fresh.isCameraEnabled());
		CURRENT.setParticlesEnabled(fresh.isParticlesEnabled());
		CURRENT.setTargetObjectIds(fresh.getTargetObjectIds());
		CURRENT.setMinGapLow(fresh.getMinGapLow());
		CURRENT.setMinGapMid(fresh.getMinGapMid());
		CURRENT.setMinGapHigh(fresh.getMinGapHigh());
	}
}
