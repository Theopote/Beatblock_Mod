package com.beatblock.timeline.project;

import org.jspecify.annotations.Nullable;

/**
 * Single unsaved-changes gate for New / Open / Close.
 * UI renders one modal; this coordinator owns pending intent + decisions.
 */
public final class UnsavedChangesCoordinator {

	public enum Intent {
		NEW_PROJECT,
		OPEN_PROJECT,
		CLOSE_BEATBLOCK
	}

	public enum Gate {
		/** Dirty: show Save / Don't Save / Cancel. */
		NEEDS_CONFIRM,
		/** Clean: run the action immediately. */
		PROCEED
	}

	public enum SaveChoice {
		/** Current path known — call {@link ProjectSessionController#save()}. */
		SAVE,
		/** No path — open Save As, then continue pending on success. */
		SAVE_AS,
		/** Stay on current project; clear pending. */
		CANCELLED_NO_PATH
	}

	private @Nullable Intent pending;

	public Gate request(Intent intent, boolean dirty) {
		if (intent == null) {
			throw new IllegalArgumentException("intent");
		}
		if (!dirty) {
			pending = null;
			return Gate.PROCEED;
		}
		pending = intent;
		return Gate.NEEDS_CONFIRM;
	}

	public @Nullable Intent pending() {
		return pending;
	}

	public boolean hasPending() {
		return pending != null;
	}

	/** Don't Save — consume and return pending intent to execute. */
	public @Nullable Intent confirmDiscard() {
		Intent action = pending;
		pending = null;
		return action;
	}

	/**
	 * Save chosen in the unsaved modal.
	 * @param hasProjectPath whether Save (not Save As) can run immediately
	 */
	public SaveChoice chooseSave(boolean hasProjectPath) {
		if (pending == null) {
			return SaveChoice.CANCELLED_NO_PATH;
		}
		if (!hasProjectPath) {
			// Keep pending; UI opens Save As and resumes after success.
			return SaveChoice.SAVE_AS;
		}
		return SaveChoice.SAVE;
	}

	/** After successful Save / Save As while a gate is pending — continue the action. */
	public @Nullable Intent continueAfterSuccessfulSave() {
		Intent action = pending;
		pending = null;
		return action;
	}

	public void cancel() {
		pending = null;
	}
}
