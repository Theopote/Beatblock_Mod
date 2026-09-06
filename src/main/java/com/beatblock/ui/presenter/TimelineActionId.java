package com.beatblock.ui.presenter;

/** 可由菜单、快捷键或其他 UI 入口触发的统一时间线动作。 */
public enum TimelineActionId {
	UNDO,
	REDO,
	CUT,
	COPY,
	PASTE_AT_PLAYHEAD,
	DELETE,
	DUPLICATE,
	SPLIT_AT_PLAYHEAD,
	ADD_MARKER_AT_PLAYHEAD,
	RUN_BINDING_MAP,
	RUN_AUTO_MAP,
	BAKE_STEP,
	GENERATE_RHYTHM_DROP
}
