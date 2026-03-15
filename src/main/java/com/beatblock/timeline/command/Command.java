package com.beatblock.timeline.command;

/**
 * 可撤销命令接口：Undo/Redo 基础。
 */
public interface Command {

	void execute();

	void undo();
}
