package com.beatblock.timeline.command;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 命令管理：执行、撤销、重做。
 */
public final class CommandManager {

	private final Deque<Command> undoStack = new ArrayDeque<>();
	private final Deque<Command> redoStack = new ArrayDeque<>();
	private static final int MAX_UNDO = 128;

	public void execute(@Nullable Command cmd) {
		if (cmd == null) return;
		Command toExecute = cmd;
		if (!undoStack.isEmpty()) {
			Command top = undoStack.peek();
			if (top instanceof MergeableCommand mergeable && mergeable.canMergeWith(cmd)) {
				toExecute = mergeable.mergeWith(cmd);
				undoStack.pop();
			}
		}
		toExecute.execute();
		undoStack.push(toExecute);
		if (undoStack.size() > MAX_UNDO) undoStack.removeLast();
		redoStack.clear();
	}

	public void undo() {
		if (undoStack.isEmpty()) return;
		Command cmd = undoStack.pop();
		cmd.undo();
		redoStack.push(cmd);
	}

	public void redo() {
		if (redoStack.isEmpty()) return;
		Command cmd = redoStack.pop();
		cmd.execute();
		undoStack.push(cmd);
	}

	public boolean canUndo() { return !undoStack.isEmpty(); }
	public boolean canRedo() { return !redoStack.isEmpty(); }
	public void clear() { undoStack.clear(); redoStack.clear(); }

	public int undoCount() { return undoStack.size(); }
	public int redoCount() { return redoStack.size(); }

	public List<String> undoDescriptionsNewestFirst() {
		return describeStack(undoStack);
	}

	public List<String> redoDescriptionsNewestFirst() {
		return describeStack(redoStack);
	}

	private static List<String> describeStack(Deque<Command> stack) {
		List<String> out = new ArrayList<>(stack.size());
		for (Command command : stack) {
			out.add(CommandDescriptions.describe(command));
		}
		return out;
	}
}
