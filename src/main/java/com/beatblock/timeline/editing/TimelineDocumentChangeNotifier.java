package com.beatblock.timeline.editing;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.project.ProjectSessionState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single exit for committed Timeline document mutations.
 * <p>
 * Call only after a gesture/command commit (mouseup, Apply, Delete, Paste, Split, Undo/Redo) —
 * never during live drag preview frames.
 * <p>
 * Side effects: refresh formal compiled playback while driving, and bump
 * {@link ProjectSessionState} dirty generation. Additional listeners may register here.
 */
public final class TimelineDocumentChangeNotifier {

	@FunctionalInterface
	public interface Listener {
		void onDocumentEdited();
	}

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

	static {
		LISTENERS.add(ProjectSessionState.get()::markDocumentEdited);
	}

	private TimelineDocumentChangeNotifier() {}

	/** Register an extra listener (tests / future panels). Default session dirty hook is always present. */
	public static void addListener(Listener listener) {
		if (listener != null) {
			LISTENERS.add(listener);
		}
	}

	public static void removeListener(Listener listener) {
		if (listener != null) {
			LISTENERS.remove(listener);
		}
	}

	/** Shared with Properties {@code afterDocumentEdit} and Timeline gesture commits. */
	public static void notifyDocumentEdited() {
		try {
			BeatBlockClientDriver.reloadCompiledPlaybackIfDriving();
		} catch (Throwable error) {
			BeatBlock.LOGGER.debug("Skip timeline document-change playback refresh", error);
		}
		for (Listener listener : LISTENERS) {
			try {
				listener.onDocumentEdited();
			} catch (Throwable error) {
				BeatBlock.LOGGER.debug("Skip timeline document-change listener", error);
			}
		}
	}
}
