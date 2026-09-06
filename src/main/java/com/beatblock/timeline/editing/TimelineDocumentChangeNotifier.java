package com.beatblock.timeline.editing;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;

/**
 * Single exit for committed Timeline document mutations.
 * <p>
 * Call only after a gesture/command commit (mouseup, Apply, Delete, Paste, Split, Undo/Redo) —
 * never during live drag preview frames.
 * <p>
 * Today this refreshes formal compiled playback while driving. Future listeners
 * (compiler invalidation, property panel sync) should register here instead of
 * calling {@link BeatBlockClientDriver} from each interaction path.
 */
public final class TimelineDocumentChangeNotifier {

	private TimelineDocumentChangeNotifier() {}

	/** Shared with Properties {@code afterDocumentEdit} and Timeline gesture commits. */
	public static void notifyDocumentEdited() {
		try {
			BeatBlockClientDriver.reloadCompiledPlaybackIfDriving();
		} catch (Throwable error) {
			BeatBlock.LOGGER.debug("Skip timeline document-change side effects", error);
		}
	}
}
