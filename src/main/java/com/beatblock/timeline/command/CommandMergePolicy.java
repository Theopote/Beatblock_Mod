package com.beatblock.timeline.command;

/**
 * 命令合并默认策略。
 */
public final class CommandMergePolicy {

	public static final long DEFAULT_MERGE_WINDOW_MS = 1000L;

	private CommandMergePolicy() {}

	public static boolean withinMergeWindow(long anchorMs, long windowMs) {
		return anchorMs >= 0L && System.currentTimeMillis() - anchorMs <= windowMs;
	}
}
