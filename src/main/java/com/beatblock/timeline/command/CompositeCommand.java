package com.beatblock.timeline.command;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将多条命令合并为一步 Undo/Redo。
 */
public final class CompositeCommand implements Command {

	private final List<Command> commands;

	public CompositeCommand(@Nullable List<Command> commands) {
		this.commands = commands != null ? List.copyOf(commands) : List.of();
	}

	public static @NonNull CompositeCommand of(@Nullable Command... commands) {
		if (commands == null || commands.length == 0) {
			return new CompositeCommand(List.of());
		}
		List<Command> list = new ArrayList<>(commands.length);
		Collections.addAll(list, commands);
		return new CompositeCommand(list);
	}

	@Override
	public void execute() {
		for (Command command : commands) {
			if (command != null) command.execute();
		}
	}

	@Override
	public void undo() {
		for (int i = commands.size() - 1; i >= 0; i--) {
			Command command = commands.get(i);
			if (command != null) command.undo();
		}
	}
}
