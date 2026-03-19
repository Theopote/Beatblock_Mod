package com.beatblock.timeline.rendering;

/**
 * 轨道行元数据：默认名称、层级（一级轨道 / 子轨道），与 TimelineLayout.CONTENT_ROW_COUNT 对应。
 * 音频为一组，下辖波形、低频、中频、高频；动画为一组，下辖方块动画、自动动画。
 */
public final class TimelineTrackMeta {

	public static final int NO_PARENT = -1;

	private static final String[] DEFAULT_NAMES = {
		"音频",   // 0 一级
		"波形",   // 1 音频子轨道
		"低频",   // 2 音频子轨道
		"中频",   // 3 音频子轨道
		"高频",   // 4 音频子轨道
		"动画",   // 5 一级
		"方块动画", // 6 动画子轨道
		"自动动画", // 7 动画子轨道
		"摄像机",  // 8 一级
		"事件"    // 9 一级
	};

	/** 父行索引，NO_PARENT 表示一级轨道（或组标题） */
	private static final int[] PARENT_ROW = {
		NO_PARENT, // 0 音频
		0,         // 1 波形
		0,         // 2 低频
		0,         // 3 中频
		0,         // 4 高频
		NO_PARENT, // 5 动画
		5,         // 6 方块动画
		5,         // 7 自动动画
		NO_PARENT, // 8 摄像机
		NO_PARENT  // 9 事件
	};

	public static String getDefaultName(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= DEFAULT_NAMES.length) return "";
		return DEFAULT_NAMES[rowIndex];
	}

	public static boolean isGroupRow(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= PARENT_ROW.length) return false;
		return PARENT_ROW[rowIndex] == NO_PARENT && (rowIndex == 0 || rowIndex == 5);
	}

	/** 是否有父轨道（是否为子轨道，需要缩进显示） */
	public static boolean hasParent(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= PARENT_ROW.length) return false;
		return PARENT_ROW[rowIndex] != NO_PARENT;
	}

	public static int getParentRowIndex(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= PARENT_ROW.length) return NO_PARENT;
		return PARENT_ROW[rowIndex];
	}

	/**
	 * 轨道「类型」列文案：音频组与子轨、动画组与子轨、摄像机、事件（用于时间线左侧表头）。
	 */
	public static String getCategoryTypeLabel(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= DEFAULT_NAMES.length) return "";
		if (rowIndex <= 4) return "音频";
		if (rowIndex <= 7) return "动画";
		if (rowIndex == 8) return "摄像机";
		if (rowIndex == 9) return "事件";
		return "";
	}
}
