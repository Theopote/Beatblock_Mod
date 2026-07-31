package com.beatblock.timeline.playback;

/**
 * 时间线编译阶段遇到不可恢复的数据错误时抛出。
 * <p>
 * 典型场景：播放快照需要强不可变参数，但事件参数中出现了无法安全冻结的自定义可变类型。
 */
public final class TimelineCompilationException extends RuntimeException {

	public TimelineCompilationException(String message) {
		super(message);
	}

	public TimelineCompilationException(String message, Throwable cause) {
		super(message, cause);
	}
}
