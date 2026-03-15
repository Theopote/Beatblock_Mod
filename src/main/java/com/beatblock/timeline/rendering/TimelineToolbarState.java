package com.beatblock.timeline.rendering;

/**
 * 时间线工具栏状态：播放控制由外部（MusicPlayer/Clock）处理，此处仅保存吸附与显示选项。
 */
public final class TimelineToolbarState {

	/** 拖拽/移动时吸附到网格 */
	private boolean snapToGrid = true;
	/** 吸附到节拍（Beat） */
	private boolean snapToBeat = true;
	/** 吸附到其他事件/关键帧（Magnet） */
	private boolean magnetSnap = true;
	/** 显示节拍网格线（与时间网格可独立） */
	private boolean beatGridVisible = true;
	/** 循环播放 */
	private boolean loop = false;

	public boolean isSnapToGrid() { return snapToGrid; }
	public void setSnapToGrid(boolean v) { snapToGrid = v; }

	public boolean isSnapToBeat() { return snapToBeat; }
	public void setSnapToBeat(boolean v) { snapToBeat = v; }

	public boolean isMagnetSnap() { return magnetSnap; }
	public void setMagnetSnap(boolean v) { magnetSnap = v; }

	public boolean isBeatGridVisible() { return beatGridVisible; }
	public void setBeatGridVisible(boolean v) { beatGridVisible = v; }

	public boolean isLoop() { return loop; }
	public void setLoop(boolean v) { loop = v; }
}
