package com.beatblock.client;

import com.beatblock.engine.BuildExecutionPolicy;

/**
 * 播放执行模式：区分编辑预览、实时播放与导出重建对 World State 的处理策略。
 * <p>
 * Render State（ANIMATE）三种模式均可；World State（BUILD / PLACE / CLEAR）仅
 * {@link #REALTIME_PLAYBACK} / {@link #EXPORT_RECONSTRUCTION} 写入权威世界，且导出必须
 * 一次重建目标帧（无 mutation 限流），不能复用实时播放的 per-tick 预算。
 * <p>
 * BUILD 细则见 {@link #buildPolicy()}。
 */
public enum PlaybackExecutionMode {
	/** Scrub / 编辑态：视觉预览，不写权威世界，mutation 预算无上限。 */
	EDITOR_PREVIEW,
	/** 正式播放：权威世界写入，每 tick 限流以避免卡顿。 */
	REALTIME_PLAYBACK,
	/** 导出离线重建：权威世界写入，无预算上限，由导出协调器独占驱动。 */
	EXPORT_RECONSTRUCTION;

	/** @deprecated 使用 {@link BuildExecutionPolicy#REALTIME_MUTATION_BUDGET_PER_TICK} */
	@Deprecated
	public static final int REALTIME_MUTATION_BUDGET_PER_TICK =
		BuildExecutionPolicy.REALTIME_MUTATION_BUDGET_PER_TICK;

	public boolean isPreviewOnly() {
		return this == EDITOR_PREVIEW;
	}

	public boolean writesAuthoritativeWorld() {
		return buildPolicy().writesAuthoritativeWorld();
	}

	public int mutationBudgetPerTick() {
		return buildPolicy().mutationBudgetPerTick();
	}

	/** 导出期间普通 client tick 不得推进同一引擎。 */
	public boolean blocksOrdinaryClientTick() {
		return this == EXPORT_RECONSTRUCTION;
	}

	public BuildExecutionPolicy buildPolicy() {
		return switch (this) {
			case EDITOR_PREVIEW -> BuildExecutionPolicy.PREVIEW;
			case REALTIME_PLAYBACK -> BuildExecutionPolicy.REALTIME;
			case EXPORT_RECONSTRUCTION -> BuildExecutionPolicy.OFFLINE_EXPORT;
		};
	}
}
