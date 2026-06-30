package com.beatblock.timeline.command;

import com.beatblock.ui.i18n.BBTexts;

/** 撤销/重做历史的人类可读描述。 */
public final class CommandDescriptions {

	private CommandDescriptions() {
	}

	public static String describe(Command command) {
		if (command == null) {
			return "";
		}
		if (command instanceof AddTimelineAnimationEventCommand) {
			return BBTexts.get("beatblock.undo.add_animation");
		}
		if (command instanceof UpdateAnimationEventCommand) {
			return BBTexts.get("beatblock.undo.update_animation");
		}
		if (command instanceof MoveEventCommand) {
			return BBTexts.get("beatblock.undo.move_event");
		}
		if (command instanceof DeleteEventCommand) {
			return BBTexts.get("beatblock.undo.delete_event");
		}
		if (command instanceof AddEventCommand) {
			return BBTexts.get("beatblock.undo.add_event");
		}
		if (command instanceof ApplyClipDragCommand) {
			return BBTexts.get("beatblock.undo.move_clip");
		}
		if (command instanceof ClearAnimationTrackCommand) {
			return BBTexts.get("beatblock.undo.clear_track");
		}
		if (command instanceof CompositeCommand composite) {
			return BBTexts.get("beatblock.undo.composite", composite.commandCount());
		}
		if (command instanceof com.beatblock.timeline.command.layer.CreateLayerCommand) {
			return BBTexts.get("beatblock.undo.create_layer");
		}
		if (command instanceof com.beatblock.timeline.command.layer.DeleteLayerCommand) {
			return BBTexts.get("beatblock.undo.delete_layer");
		}
		if (command instanceof com.beatblock.timeline.command.layer.RenameLayerCommand) {
			return BBTexts.get("beatblock.undo.rename_layer");
		}
		if (command instanceof com.beatblock.timeline.command.layer.ToggleLayerVisibilityCommand) {
			return BBTexts.get("beatblock.undo.toggle_layer");
		}
		if (command instanceof com.beatblock.timeline.command.layer.BindLayerToTrackCommand) {
			return BBTexts.get("beatblock.undo.bind_layer");
		}
		if (command instanceof com.beatblock.timeline.command.layer.GroupLayersCommand) {
			return BBTexts.get("beatblock.undo.group_layers");
		}
		if (command instanceof com.beatblock.timeline.command.layer.UngroupLayersCommand) {
			return BBTexts.get("beatblock.undo.ungroup_layers");
		}
		if (command instanceof com.beatblock.timeline.command.layer.MergeLayersCommand) {
			return BBTexts.get("beatblock.undo.merge_layers");
		}
		if (command instanceof com.beatblock.timeline.command.layer.SetLayerColorCommand
			|| command instanceof com.beatblock.timeline.command.layer.SetGroupColorCommand) {
			return BBTexts.get("beatblock.undo.set_layer_color");
		}
		if (command instanceof com.beatblock.timeline.command.layer.RenameGroupCommand) {
			return BBTexts.get("beatblock.undo.rename_group");
		}
		if (command instanceof com.beatblock.timeline.command.layer.ReorderLayerCommand) {
			return BBTexts.get("beatblock.undo.reorder_layer");
		}
		if (command instanceof com.beatblock.timeline.command.layer.CreateBuildLayerTrackCommand) {
			return BBTexts.get("beatblock.undo.create_build_layer_track");
		}
		if (command instanceof com.beatblock.timeline.command.layer.DeleteBuildLayerTrackCommand) {
			return BBTexts.get("beatblock.undo.delete_build_layer_track");
		}
		return command.getClass().getSimpleName();
	}
}
