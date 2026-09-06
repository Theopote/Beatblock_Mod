package com.beatblock.timeline.command;

/**
 * Commands that may refuse to mutate the document.
 * {@link CommandManager} only pushes undo when {@link #wasApplied()} is true after {@link #execute()}.
 */
public interface AppliedCommand extends Command {

	/** {@code true} after a successful {@link #execute()} that mutated (or legitimately applied). */
	boolean wasApplied();
}
