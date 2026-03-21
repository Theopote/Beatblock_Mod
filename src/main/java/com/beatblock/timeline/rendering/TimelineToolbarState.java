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
	/** 循环起点（秒） */
	private double loopInSeconds = 0;
	/** 循环终点（秒），0 表示未设置 */
	private double loopOutSeconds = 0;

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

	public double getLoopInSeconds() { return loopInSeconds; }
	public void setLoopInSeconds(double v) { loopInSeconds = Math.max(0, v); }

	public double getLoopOutSeconds() { return loopOutSeconds; }
	public void setLoopOutSeconds(double v) { loopOutSeconds = Math.max(0, v); }

	/** 是否存在有效循环区间（Out > In）。 */
	public boolean hasLoopRange() {
		return loopOutSeconds > loopInSeconds;
	}

	/** 清空循环区，保留 loop 开关状态。 */
	public void clearLoopRange() {
		loopInSeconds = 0;
		loopOutSeconds = 0;
	}
}
