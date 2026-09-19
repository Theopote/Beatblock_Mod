package com.beatblock.engine;

/**
 * BUILD 执行策略：把原先塞进 {@code previewOnly} boolean 的语义拆开。
 * <ul>
 *   <li>{@link #PREVIEW} — 只解析视觉态，不写权威世界</li>
 *   <li>{@link #REALTIME} — 实时演出：限流；未加载区块跳过并推进 placedCount</li>
 *   <li>{@link #OFFLINE_EXPORT} — 离线重建：无预算；未加载区块不推进，等待加载</li>
 * </ul>
 */
public enum BuildExecutionPolicy {
	PREVIEW,
	REALTIME,
	OFFLINE_EXPORT;

	public static final int REALTIME_MUTATION_BUDGET_PER_TICK = 768;

	public int mutationBudgetPerTick() {
		return this == REALTIME ? REALTIME_MUTATION_BUDGET_PER_TICK : Integer.MAX_VALUE;
	}

	public boolean writesAuthoritativeWorld() {
		return this == REALTIME || this == OFFLINE_EXPORT;
	}

	/**
	 * 未加载区块策略。
	 * <p>
	 * REALTIME / PREVIEW：跳过并推进（避免远距 chunk 卡住实例）。
	 * OFFLINE_EXPORT：不推进，本 tick 停在该块，等待 chunk 加载后再写。
	 */
	public boolean advancePastUnloadedChunks() {
		return this != OFFLINE_EXPORT;
	}

	public boolean requiresLoadedChunks() {
		return this == OFFLINE_EXPORT;
	}
}
