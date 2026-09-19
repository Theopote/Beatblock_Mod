package com.beatblock.client;

/**
 * 播放执行模式：区分编辑预览、实时播放与导出重建对 World State 的处理策略。
 * <p>
 * Render State（ANIMATE）三种模式均可；World State（BUILD / PLACE / CLEAR）仅
 * {@link #REALTIME_PLAYBACK} / {@link #EXPORT_RECONSTRUCTION} 写入权威世界，且导出必须
 * 一次重建目标帧（无 mutation 限流），不能复用实时播放的 per-tick 预算。
 */
public enum PlaybackExecutionMode {
	/** Scrub / 编辑态：视觉预览，不写权威世界，mutation 预算无上限。 */
	EDITOR_PREVIEW,
	/** 正式播放：权威世界写入，每 tick 限流以避免卡顿。 */
	REALTIME_PLAYBACK,
	/** 导出离线重建：权威世界写入，无预算上限，由导出协调器独占驱动。 */
	EXPORT_RECONSTRUCTION;

	/** 实时播放每 tick 最多写入的 BUILD 方块数。 */
	public static final int REALTIME_MUTATION_BUDGET_PER_TICK = 768;

	public boolean isPreviewOnly() {
		return this == EDITOR_PREVIEW;
	}

	public boolean writesAuthoritativeWorld() {
		return this == REALTIME_PLAYBACK || this == EXPORT_RECONSTRUCTION;
	}

	/**
	 * BUILD 每 tick mutation 预算。导出 / 预览为无上限；实时播放限流。
	 */
	public int mutationBudgetPerTick() {
		return this == REALTIME_PLAYBACK
			? REALTIME_MUTATION_BUDGET_PER_TICK
			: Integer.MAX_VALUE;
	}

	/** 导出期间普通 client tick 不得推进同一引擎。 */
	public boolean blocksOrdinaryClientTick() {
		return this == EXPORT_RECONSTRUCTION;
	}
}
