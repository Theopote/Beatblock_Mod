package com.beatblock.engine.layer;

/**
 * 建造图层可见性生命周期。
 */
public enum LayerVisibilityState {
	/** 真实方块显示，可手动切换隐藏 */
	FREE_VISIBLE,
	/** 已捕获快照、世界写入为空气，可手动切换显示或绑定轨道 */
	FREE_HIDDEN,
	/** 已绑定 BUILD 反向轨道片段，由播放头驱动揭示，不可删除/释放 */
	BOUND_TO_TRACK
}
