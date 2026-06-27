package com.beatblock.timeline.command;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 可与栈顶命令合并为单个 Undo 单元的命令（连续微调、同一次拖动会话等）。
 */
public interface MergeableCommand extends Command {

	long mergeWindowMs();

	/**
	 * {@code other} 是否尚未 execute；通常由 {@link CommandManager} 在入栈前调用。
	 */
	boolean canMergeWith(@NonNull Command other);

	/**
	 * 与 {@code other} 合并；保留本命令的 undo 起点与 {@code other} 的 execute 终点。
	 */
	@NonNull Command mergeWith(@NonNull Command other);
}
