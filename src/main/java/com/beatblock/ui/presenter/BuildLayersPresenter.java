package com.beatblock.ui.presenter;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.selection.BeatBlockSelectionManager;
import com.beatblock.timeline.StageObjectReferenceService;
import com.beatblock.timeline.StageObjectTargetConflictFinder;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.layer.CreateLayerCommand;
import com.beatblock.timeline.command.layer.DeleteLayerCommand;
import com.beatblock.timeline.command.layer.GroupLayersCommand;
import com.beatblock.timeline.command.layer.MergeLayersCommand;
import com.beatblock.timeline.command.layer.ReorderLayerCommand;
import com.beatblock.timeline.command.layer.RenameGroupCommand;
import com.beatblock.timeline.command.layer.RenameLayerCommand;
import com.beatblock.timeline.command.layer.SetGroupColorCommand;
import com.beatblock.timeline.command.layer.SetLayerColorCommand;
import com.beatblock.timeline.command.layer.ToggleLayerVisibilityCommand;
import com.beatblock.timeline.command.layer.UngroupLayersCommand;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 建造图层面板业务逻辑：校验、Command 封装与提交。
 */
public final class BuildLayersPresenter {

	public record RenameOutcome(PresenterResult result, String committedName) {}

	public record CreateOutcome(
		PresenterResult result,
		String createdLayerId,
		List<BlockPos> blocksToRemoveFromSelection
	) {}

	public record ToggleVisibilityOutcome(PresenterResult result) {}

	public record DeleteOutcome(
		PresenterResult result,
		StageObjectReferenceService.ReferenceSummary blockedReferences
	) {
		public DeleteOutcome(PresenterResult result) {
			this(result, new StageObjectReferenceService.ReferenceSummary(java.util.List.of()));
		}
	}

	public record LayerActionOutcome(
		PresenterResult result,
		String primaryId,
		StageObjectTargetConflictFinder.ConflictSummary mergeConflicts
	) {
		public LayerActionOutcome(PresenterResult result, String primaryId) {
			this(result, primaryId, new StageObjectTargetConflictFinder.ConflictSummary(List.of()));
		}
	}

	private final Supplier<CommandManager> commandManager;
	private final Supplier<BuildLayerManager> layerManager;
	private final Supplier<Timeline> timeline;
	private final Supplier<BeatBlockSelectionManager> selectionManager;

	public BuildLayersPresenter(
		Supplier<CommandManager> commandManager,
		Supplier<BuildLayerManager> layerManager
	) {
		this(commandManager, layerManager, () -> null, () -> null);
	}

	public BuildLayersPresenter(
		Supplier<CommandManager> commandManager,
		Supplier<BuildLayerManager> layerManager,
		Supplier<Timeline> timeline
	) {
		this(commandManager, layerManager, timeline, () -> null);
	}

	public BuildLayersPresenter(
		Supplier<CommandManager> commandManager,
		Supplier<BuildLayerManager> layerManager,
		Supplier<Timeline> timeline,
		Supplier<BeatBlockSelectionManager> selectionManager
	) {
		this.commandManager = commandManager;
		this.layerManager = layerManager;
		this.timeline = timeline != null ? timeline : () -> null;
		this.selectionManager = selectionManager != null ? selectionManager : () -> null;
	}

	public int worldSelectionCount() {
		BeatBlockSelectionManager selection = selectionManager.get();
		return selection != null ? selection.getSelectionCount() : 0;
	}

	/**
	 * Creates a layer from the injected world selection and clears claimed blocks from that selection.
	 */
	public CreateOutcome createLayerFromWorldSelection(String rawName) {
		BeatBlockSelectionManager selection = selectionManager.get();
		if (selection == null) {
			return new CreateOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.create_selection_first")),
				null,
				List.of()
			);
		}
		CreateOutcome outcome = createLayerFromSelection(
			rawName,
			new ArrayList<>(selection.getSelectedBlocks())
		);
		if (outcome.createdLayerId() != null && !outcome.blocksToRemoveFromSelection().isEmpty()) {
			selection.removeBlocks(outcome.blocksToRemoveFromSelection());
		}
		return outcome;
	}

	public Set<String> selectedLayerIds() {
		BuildLayerManager manager = layerManager.get();
		return manager != null ? manager.getSelectedLayerIds() : Set.of();
	}

	public boolean isLayerSelected(String layerId) {
		BuildLayerManager manager = layerManager.get();
		return manager != null && manager.isLayerSelected(layerId);
	}

	public void selectLayer(String layerId, boolean ctrl, boolean shift, List<String> displayOrder) {
		BuildLayerManager manager = layerManager.get();
		if (manager == null) {
			return;
		}
		manager.selectLayer(layerId, ctrl, shift, displayOrder);
	}

	public void clearSelection() {
		BuildLayerManager manager = layerManager.get();
		if (manager != null) {
			manager.clearSelection();
		}
	}

	public PresenterResult reorderLayerBefore(String movingLayerId, String targetLayerId) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable"));
		}
		if (movingLayerId == null || targetLayerId == null || movingLayerId.equals(targetLayerId)) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.layer_reorder_invalid"));
		}
		BuildLayer moving = manager.get(movingLayerId);
		BuildLayer target = manager.get(targetLayerId);
		if (moving == null || target == null
			|| !java.util.Objects.equals(moving.getGroupId(), target.getGroupId())) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.layer_reorder_invalid"));
		}
		commands.execute(new ReorderLayerCommand(manager, movingLayerId, targetLayerId));
		return PresenterResult.success("");
	}

	public List<String> buildDisplayOrder() {
		BuildLayerManager manager = layerManager.get();
		if (manager == null) {
			return List.of();
		}
		List<String> order = new ArrayList<>();
		for (BuildLayerGroup group : manager.getAllGroups()) {
			for (BuildLayer layer : manager.getLayersInGroup(group.getId())) {
				order.add(layer.getId());
			}
		}
		for (BuildLayer layer : manager.getUngroupedLayers()) {
			order.add(layer.getId());
		}
		return order;
	}

	public RenameOutcome renameLayer(String layerId, String rawName) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new RenameOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")), null);
		}
		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new RenameOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.layer_not_found")), null);
		}

		String trimmed = rawName != null ? rawName.trim() : "";
		if (trimmed.isEmpty()) {
			return new RenameOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.layer_name_empty")),
				layer.getName()
			);
		}
		if (trimmed.equals(layer.getName())) {
			return new RenameOutcome(PresenterResult.success(""), trimmed);
		}
		if (manager.isNameTaken(trimmed, layer.getId())) {
			return new RenameOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.layer_name_taken", trimmed)),
				layer.getName()
			);
		}

		commands.execute(new RenameLayerCommand(manager, layer.getId(), trimmed));
		return new RenameOutcome(PresenterResult.success(BBTexts.get("beatblock.message.layer_renamed", trimmed)), trimmed);
	}

	public RenameOutcome renameGroup(String groupId, String rawName) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new RenameOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")), null);
		}
		BuildLayerGroup group = manager.getGroup(groupId);
		if (group == null) {
			return new RenameOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.group_not_found")), null);
		}
		String trimmed = rawName != null ? rawName.trim() : "";
		if (trimmed.isEmpty()) {
			return new RenameOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.layer_name_empty")),
				group.getName()
			);
		}
		if (manager.isGroupNameTaken(trimmed, group.getId())) {
			return new RenameOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.group_name_taken", trimmed)),
				group.getName()
			);
		}
		commands.execute(new RenameGroupCommand(manager, group.getId(), trimmed));
		return new RenameOutcome(PresenterResult.success(BBTexts.get("beatblock.message.group_renamed", trimmed)), trimmed);
	}

	public CreateOutcome createLayerFromSelection(String rawName, List<BlockPos> selectedBlocks) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new CreateOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.engine_or_timeline_unavailable")), null, List.of());
		}

		List<BlockPos> blocks = selectedBlocks != null ? selectedBlocks : List.of();
		if (blocks.isEmpty()) {
			return new CreateOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.create_selection_first")), null, List.of());
		}

		int claimed = manager.countClaimedBlocks(blocks);
		if (claimed >= blocks.size()) {
			return new CreateOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.all_blocks_claimed")),
				null,
				List.of()
			);
		}

		String name = rawName != null ? rawName.trim() : "";
		var cmd = new CreateLayerCommand(manager, name.isEmpty() ? "layer" : name, blocks);
		commands.execute(cmd);

		BuildLayer created = cmd.getCreatedLayer();
		if (created == null) {
			return new CreateOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.create_layer_failed")), null, List.of());
		}

		String message = claimed > 0
			? BBTexts.get("beatblock.message.layer_created_skipped", created.getName(), claimed)
			: BBTexts.get("beatblock.message.layer_created_hidden", created.getName());
		manager.setSelectionTo(created.getId());
		return new CreateOutcome(
			PresenterResult.success(message),
			created.getId(),
			new ArrayList<>(created.getStageObject().getBlocks())
		);
	}

	public BuildLayerManager currentLayerManager() {
		return layerManager.get();
	}

	public BuildLayer findLayer(String layerId) {
		BuildLayerManager manager = layerManager.get();
		if (manager == null || layerId == null || layerId.isBlank()) {
			return null;
		}
		return manager.get(layerId);
	}

	public BuildLayerGroup findGroup(String groupId) {
		BuildLayerManager manager = layerManager.get();
		if (manager == null || groupId == null || groupId.isBlank()) {
			return null;
		}
		return manager.getGroup(groupId);
	}

	public ToggleVisibilityOutcome toggleVisibility(String layerId) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new ToggleVisibilityOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")));
		}

		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new ToggleVisibilityOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.layer_not_found")));
		}

		World world = BuildLayerManager.currentWorld();
		if (world == null) {
			return new ToggleVisibilityOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.no_world_context")));
		}

		boolean wasVisible = layer.getState() == LayerVisibilityState.FREE_VISIBLE;
		commands.execute(new ToggleLayerVisibilityCommand(manager, layer.getId()));
		String message = wasVisible
			? BBTexts.get("beatblock.message.layer_hidden")
			: BBTexts.get("beatblock.message.layer_shown");
		return new ToggleVisibilityOutcome(PresenterResult.success(message));
	}

	public StageObjectReferenceService.ReferenceSummary findStageObjectReferences(String layerId) {
		BuildLayerManager manager = layerManager.get();
		if (manager == null || layerId == null || layerId.isBlank()) {
			return new StageObjectReferenceService.ReferenceSummary(java.util.List.of());
		}
		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new StageObjectReferenceService.ReferenceSummary(java.util.List.of());
		}
		String stageId = layer.getStageObjectId();
		if (stageId == null || stageId.isBlank()) {
			return new StageObjectReferenceService.ReferenceSummary(java.util.List.of());
		}
		return StageObjectReferenceService.find(timeline.get(), java.util.Set.of(stageId));
	}

	public DeleteOutcome deleteLayer(String layerId) {
		return deleteLayer(layerId, false);
	}

	/**
	 * @param clearReferences when true, unbind Timeline / AutoMap / choreography targets before delete
	 *                        (Strategy A confirm path: "Remove references and delete").
	 */
	public DeleteOutcome deleteLayer(String layerId, boolean clearReferences) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new DeleteOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")));
		}

		BuildLayer layer = manager.get(layerId);
		if (layer == null) {
			return new DeleteOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.layer_not_found")));
		}
		if (!layer.canDelete()) {
			return new DeleteOutcome(PresenterResult.failure(BBTexts.get("beatblock.layer.cannot_delete_bound")));
		}

		StageObjectReferenceService.ReferenceSummary refs = findStageObjectReferences(layerId);
		if (!refs.isEmpty() && !clearReferences) {
			return new DeleteOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.layer.delete_blocked_by_refs", refs.count())),
				refs
			);
		}

		String layerName = layer.getName();
		commands.execute(new DeleteLayerCommand(manager, layer.getId(), timeline.get(), clearReferences));
		return new DeleteOutcome(PresenterResult.success(BBTexts.get("beatblock.message.layer_deleted", layerName)));
	}

	public LayerActionOutcome groupSelectedLayers(String rawName) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new LayerActionOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")), null);
		}
		Set<String> selected = manager.getSelectedLayerIds();
		if (selected.size() < 2) {
			return new LayerActionOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.group_need_two_layers")), null);
		}
		String name = rawName != null && !rawName.isBlank() ? rawName.trim() : "group";
		var cmd = new GroupLayersCommand(manager, name, List.copyOf(selected));
		commands.execute(cmd);
		BuildLayerGroup group = cmd.getCreatedGroup();
		if (group == null) {
			return new LayerActionOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.group_failed")), null);
		}
		return new LayerActionOutcome(
			PresenterResult.success(BBTexts.get("beatblock.message.group_created", group.getName())),
			group.getId()
		);
	}

	public LayerActionOutcome ungroupSelectedLayers() {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new LayerActionOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")), null);
		}
		Set<String> selected = manager.getSelectedLayerIds();
		if (selected.isEmpty()) {
			return new LayerActionOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.select_layers_first")), null);
		}
		commands.execute(new UngroupLayersCommand(manager, List.copyOf(selected)));
		return new LayerActionOutcome(PresenterResult.success(BBTexts.get("beatblock.message.ungrouped")), null);
	}

	public LayerActionOutcome mergeSelectedLayers(String rawName) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null) {
			return new LayerActionOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable")), null);
		}
		Set<String> selected = manager.getSelectedLayerIds();
		if (selected.size() < 2) {
			return new LayerActionOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.merge_need_two_layers")), null);
		}
		for (String layerId : selected) {
			BuildLayer layer = manager.get(layerId);
			if (layer == null || !layer.canDelete()) {
				return new LayerActionOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.merge_not_allowed")), null);
			}
		}
		var cmd = new MergeLayersCommand(manager, List.copyOf(selected), rawName, timeline.get());
		commands.execute(cmd);
		BuildLayer merged = cmd.getMergedLayer();
		if (merged == null) {
			return new LayerActionOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.merge_failed")), null);
		}
		manager.setSelectionTo(merged.getId());
		StageObjectTargetConflictFinder.ConflictSummary conflicts =
			StageObjectTargetConflictFinder.findOverlaps(timeline.get(), merged.getStageObjectId());
		String message = conflicts.isEmpty()
			? BBTexts.get("beatblock.message.layers_merged", merged.getName())
			: BBTexts.get(
				"beatblock.message.layers_merged_with_conflicts",
				merged.getName(),
				conflicts.count()
			);
		return new LayerActionOutcome(
			PresenterResult.success(message),
			merged.getId(),
			conflicts
		);
	}

	public PresenterResult setLayerColor(String layerId, int colorArgb) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null || manager.get(layerId) == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.layer_not_found"));
		}
		if (manager.get(layerId).getColorArgb() != colorArgb) {
			commands.execute(new SetLayerColorCommand(manager, layerId, colorArgb));
		}
		return PresenterResult.success("");
	}

	public PresenterResult setGroupColor(String groupId, int colorArgb) {
		CommandManager commands = commandManager.get();
		BuildLayerManager manager = layerManager.get();
		if (commands == null || manager == null || manager.getGroup(groupId) == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.group_not_found"));
		}
		if (manager.getGroup(groupId).getColorArgb() != colorArgb) {
			commands.execute(new SetGroupColorCommand(manager, groupId, colorArgb));
		}
		return PresenterResult.success("");
	}
}
