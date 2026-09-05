package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.generation.ContentReplacePolicy;

import org.jspecify.annotations.Nullable;

/**
 * Quick Start 一键生成事务：创建 StageObject + 写入 Timeline 内容要么一起成功，要么一起回滚。
 * <p>
 * BEGIN → 记录 beforeTimeline → Create StageObject → Generate → SUCCESS 则 COMMIT；
 * 任一失败 / 异常则 ROLLBACK（删除本事务创建的 StageObject，并恢复 Timeline 快照）。
 */
public final class QuickStartGenerationTransaction {

	/**
	 * 事务状态快照（便于测试与日志）。
	 *
	 * @param createdStageObjectId 本事务创建的 StageObject；未创建时为 null
	 * @param generationId         本次生成批次 id（若下游已暴露）；未记录时为 null
	 * @param beforeTimeline       生成前的 Timeline 可编辑层快照
	 */
	public record State(
		@Nullable String createdStageObjectId,
		@Nullable String generationId,
		QuickStartTimelineSnapshot beforeTimeline
	) {}

	private @Nullable String createdStageObjectId;
	private @Nullable String generationId;
	private final QuickStartTimelineSnapshot beforeTimeline;
	private boolean committed;
	private boolean rolledBack;

	private QuickStartGenerationTransaction(QuickStartTimelineSnapshot beforeTimeline) {
		this.beforeTimeline = beforeTimeline;
	}

	public static QuickStartGenerationTransaction begin(@Nullable Timeline timeline) {
		return new QuickStartGenerationTransaction(QuickStartTimelineSnapshot.capture(timeline));
	}

	public State state() {
		return new State(createdStageObjectId, generationId, beforeTimeline);
	}

	public boolean isCommitted() {
		return committed;
	}

	public boolean isRolledBack() {
		return rolledBack;
	}

	public void recordCreatedStageObject(@Nullable String objectId) {
		if (objectId == null || objectId.isBlank()) {
			return;
		}
		createdStageObjectId = objectId.trim();
	}

	public void recordGenerationId(@Nullable String id) {
		if (id == null || id.isBlank()) {
			return;
		}
		generationId = id.trim();
	}

	public void commit() {
		if (rolledBack) {
			throw new IllegalStateException("QuickStartGenerationTransaction already rolled back");
		}
		committed = true;
	}

	/**
	 * 回滚：恢复生成前 Timeline，并删除本事务创建的 StageObject。
	 * 若已记录 {@code generationId}，额外按 generation 清理一次（双保险）。
	 */
	public void rollback(ToolPanelPresenter toolPanelPresenter, @Nullable Timeline timeline) {
		if (committed || rolledBack) {
			return;
		}
		rolledBack = true;

		if (timeline != null) {
			beforeTimeline.restore(timeline);
			if (generationId != null && !generationId.isBlank()) {
				ContentReplacePolicy byGeneration = ContentReplacePolicy.replaceGeneration(generationId);
				timeline.applyContentReplacePolicy(Timeline.TRACK_ID_ANIMATION_AUTO, byGeneration);
				timeline.applyContentReplacePolicy(Timeline.TRACK_ID_ANIMATION_BLOCK, byGeneration);
				timeline.applyContentReplacePolicy(Timeline.TRACK_ID_CAMERA, byGeneration);
				timeline.applyContentReplacePolicy(Timeline.TRACK_ID_GLOBAL, byGeneration);
			}
		}

		if (createdStageObjectId != null && toolPanelPresenter != null) {
			toolPanelPresenter.removeStageObject(createdStageObjectId);
		}
	}
}
