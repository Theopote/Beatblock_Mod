package com.beatblock.engine.influence;

/**
 * 通道占用事件总时长的比例（与 STEP 三段式 entry/idle/exit 对齐，期 2 使用）。
 */
public record DurationPolicy(float entryRatio, float idleRatio, float exitRatio) {

	public DurationPolicy {
		entryRatio = clampRatio(entryRatio);
		idleRatio = clampRatio(idleRatio);
		exitRatio = clampRatio(exitRatio);
	}

	public static DurationPolicy fullDuration() {
		return new DurationPolicy(1f, 0f, 0f);
	}

	private static float clampRatio(float value) {
		return Math.max(0f, Math.min(1f, value));
	}
}
