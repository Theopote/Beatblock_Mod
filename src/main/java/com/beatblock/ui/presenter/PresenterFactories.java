package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.CommandManager;

import java.util.function.Supplier;

public final class PresenterFactories {

	private PresenterFactories() {}

	public static BuildLayersPresenter buildLayersPresenter() {
		return new BuildLayersPresenter(
			(Supplier<CommandManager>) () -> BeatBlock.timelineEditor != null
				? BeatBlock.timelineEditor.getCommandManager()
				: null,
			(Supplier<BuildLayerManager>) () -> BeatBlock.blockAnimationEngine != null
				? BeatBlock.blockAnimationEngine.getBuildLayerManager()
				: null
		);
	}

	public static EventPropertiesPresenter eventPropertiesPresenter() {
		return EventPropertiesPresenterFactory.create();
	}

	public static SelectionPropertiesPresenter selectionPropertiesPresenter() {
		return new SelectionPropertiesPresenter(BeatBlockSelectionManager::get);
	}

	public static ToolPanelPresenter toolPanelPresenter() {
		return ToolPanelPresenterFactory.create();
	}

	public static TimelinePanelPresenter timelinePanelPresenter() {
		return new TimelinePanelPresenter();
	}

	public static TimelineEditorPresenter timelineEditorPresenter() {
		return new TimelineEditorPresenter(
			() -> BeatBlock.timelineEditor,
			time -> {
				if (BeatBlock.musicPlayer != null) {
					BeatBlock.musicPlayer.setCurrentTimeSeconds(time);
				}
			}
		);
	}

	public static MarkerPanelPresenter markerPanelPresenter() {
		return new MarkerPanelPresenter(timelineEditorPresenter());
	}

	public static MenuBarPresenter menuBarPresenter() {
		return new MenuBarPresenter(
			timelineEditorPresenter(),
			() -> BeatBlock.timeline,
			() -> BeatBlock.timelineEditor,
			() -> BeatBlock.blockAnimationEngine != null
				? BeatBlock.blockAnimationEngine.getBuildLayerManager()
				: null,
			() -> BeatBlock.audioLoader
		);
	}
}
