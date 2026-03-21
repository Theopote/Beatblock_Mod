package com.beatblock.timeline.rendering;

/**
 * 轨道行元数据：默认名称、层级（一级轨道 / 子轨道），与 TimelineLayout.CONTENT_ROW_COUNT 对应。
 * 音频为一组，下辖波形、低频、中频、高频；动画为一组，下辖方块动画、自动动画。
 */
public final class TimelineTrackMeta {

	public static final int NO_PARENT = -1;
	public static final int ROW_AUDIO_GROUP = 0;
	public static final int ROW_WAVEFORM = 1;
	public static final int ROW_FREQ_LOW = 2;
	public static final int ROW_FREQ_MID = 3;
	public static final int ROW_FREQ_HIGH = 4;
	public static final int ROW_ANIMATION_GROUP = 5;
	public static final int ROW_ANIM_BLOCK = 6;
	public static final int ROW_ANIM_AUTO = 7;
	public static final int ROW_CAMERA = 8;
	public static final int ROW_GLOBAL_EVENT = 9;
	private static final int ROW_COUNT = ROW_GLOBAL_EVENT + 1;

	private static final String[] DEFAULT_NAMES = new String[ROW_COUNT];

	/** 父行索引，NO_PARENT 表示一级轨道（或组标题） */
	private static final int[] PARENT_ROW = new int[ROW_COUNT];

	static {
		DEFAULT_NAMES[ROW_AUDIO_GROUP] = "音频";
		DEFAULT_NAMES[ROW_WAVEFORM] = "波形";
		DEFAULT_NAMES[ROW_FREQ_LOW] = "低频";
		DEFAULT_NAMES[ROW_FREQ_MID] = "中频";
		DEFAULT_NAMES[ROW_FREQ_HIGH] = "高频";
		DEFAULT_NAMES[ROW_ANIMATION_GROUP] = "动画";
		DEFAULT_NAMES[ROW_ANIM_BLOCK] = "方块动画";
		DEFAULT_NAMES[ROW_ANIM_AUTO] = "自动动画";
		DEFAULT_NAMES[ROW_CAMERA] = "摄像机";
		DEFAULT_NAMES[ROW_GLOBAL_EVENT] = "事件";

		PARENT_ROW[ROW_AUDIO_GROUP] = NO_PARENT;
		PARENT_ROW[ROW_WAVEFORM] = ROW_AUDIO_GROUP;
		PARENT_ROW[ROW_FREQ_LOW] = ROW_AUDIO_GROUP;
		PARENT_ROW[ROW_FREQ_MID] = ROW_AUDIO_GROUP;
		PARENT_ROW[ROW_FREQ_HIGH] = ROW_AUDIO_GROUP;
		PARENT_ROW[ROW_ANIMATION_GROUP] = NO_PARENT;
		PARENT_ROW[ROW_ANIM_BLOCK] = ROW_ANIMATION_GROUP;
		PARENT_ROW[ROW_ANIM_AUTO] = ROW_ANIMATION_GROUP;
		PARENT_ROW[ROW_CAMERA] = NO_PARENT;
		PARENT_ROW[ROW_GLOBAL_EVENT] = NO_PARENT;

		validateMeta();
	}

	private static void validateMeta() {
		for (int i = 0; i < ROW_COUNT; i++) {
			String name = DEFAULT_NAMES[i];
			if (name == null || name.isBlank()) {
				throw new IllegalStateException("TimelineTrackMeta missing default name at row " + i);
			}

			int parent = PARENT_ROW[i];
			if (parent == NO_PARENT) continue;
			if (parent < 0 || parent >= ROW_COUNT) {
				throw new IllegalStateException("TimelineTrackMeta parent row out of range: row=" + i + ", parent=" + parent);
			}
			if (parent == i) {
				throw new IllegalStateException("TimelineTrackMeta parent row cannot reference itself: row=" + i);
			}
			if (!isGroupRow(parent)) {
				throw new IllegalStateException("TimelineTrackMeta parent row must be a group row: row=" + i + ", parent=" + parent);
			}
		}
	}

	public static String getDefaultName(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= DEFAULT_NAMES.length) return "";
		return DEFAULT_NAMES[rowIndex];
	}

	public static boolean isGroupRow(int rowIndex) {
		if (rowIndex < 0 || rowIndex >= PARENT_ROW.length) return false;
		return PARENT_ROW[rowIndex] == NO_PARENT
			&& (rowIndex == ROW_AUDIO_GROUP || rowIndex == ROW_ANIMATION_GROUP);
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
		if (rowIndex >= ROW_AUDIO_GROUP && rowIndex <= ROW_FREQ_HIGH) return "音频";
		if (rowIndex >= ROW_ANIMATION_GROUP && rowIndex <= ROW_ANIM_AUTO) return "动画";
		if (rowIndex == ROW_CAMERA) return "摄像机";
		if (rowIndex == ROW_GLOBAL_EVENT) return "事件";
		return "";
	}
}
