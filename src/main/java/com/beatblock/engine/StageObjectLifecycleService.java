package com.beatblock.engine;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.StageObjectReferenceService;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.stageobject.DeleteStageObjectCommand;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * StageObject 生命周期：引用扫描、BuildLayer 归属判断、带引用保护的删除。
 */
public final class StageObjectLifecycleService {

	public record DeleteOutcome(
		com.beatblock.ui.presenter.PresenterResult result,
		StageObjectReferenceService.ReferenceSummary blockedReferences
	) {
		public DeleteOutcome(com.beatblock.ui.presenter.PresenterResult result) {
			this(result, new StageObjectReferenceService.ReferenceSummary(java.util.List.of()));
		}
	}

	private StageObjectLifecycleService() {}

	public static StageObjectReferenceService.ReferenceSummary findReferences(
		@Nullable Timeline timeline,
		String stageObjectId
	) {
		if (stageObjectId == null || stageObjectId.isBlank()) {
			return new StageObjectReferenceService.ReferenceSummary(java.util.List.of());
		}
		return StageObjectReferenceService.find(timeline, Set.of(stageObjectId));
	}

	public static boolean isOwnedByBuildLayer(@Nullable BuildLayerManager manager, String stageObjectId) {
		if (manager == null || stageObjectId == null || stageObjectId.isBlank()) {
			return false;
		}
		for (BuildLayer layer : manager.getAll()) {
			if (stageObjectId.equals(layer.getStageObjectId())) {
				return true;
			}
		}
		return false;
	}

	public static DeleteOutcome deleteStageObject(
		@Nullable CommandManager commands,
		@Nullable StageObjectSystem system,
		@Nullable Timeline timeline,
		@Nullable BuildLayerManager layerManager,
		String stageObjectId,
		boolean clearReferences
	) {
		if (commands == null || system == null) {
			return new DeleteOutcome(
				com.beatblock.ui.presenter.PresenterResult.failure(BBTexts.get("beatblock.message.editor_unavailable"))
			);
		}
		if (stageObjectId == null || stageObjectId.isBlank()) {
			return new DeleteOutcome(
				com.beatblock.ui.presenter.PresenterResult.failure(BBTexts.get("beatblock.message.delete_object_failed"))
			);
		}
		if (system.get(stageObjectId) == null) {
			return new DeleteOutcome(
				com.beatblock.ui.presenter.PresenterResult.failure(
					BBTexts.get("beatblock.message.object_not_found", stageObjectId)
				)
			);
		}
		if (isOwnedByBuildLayer(layerManager, stageObjectId)) {
			return new DeleteOutcome(
				com.beatblock.ui.presenter.PresenterResult.failure(
					BBTexts.get("beatblock.tool.delete_owned_by_layer")
				)
			);
		}

		StageObjectReferenceService.ReferenceSummary refs = findReferences(timeline, stageObjectId);
		if (!refs.isEmpty() && !clearReferences) {
			return new DeleteOutcome(
				com.beatblock.ui.presenter.PresenterResult.failure(
					BBTexts.get("beatblock.tool.delete_blocked_by_refs", refs.count())
				),
				refs
			);
		}

		commands.execute(new DeleteStageObjectCommand(system, timeline, stageObjectId, clearReferences));
		return new DeleteOutcome(
			com.beatblock.ui.presenter.PresenterResult.success(
				BBTexts.get("beatblock.message.object_deleted", stageObjectId)
			)
		);
	}
}
