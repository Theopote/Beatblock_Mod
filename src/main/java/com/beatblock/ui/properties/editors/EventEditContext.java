package com.beatblock.ui.properties.editors;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.presenter.AnimationEditorViewState;
import com.beatblock.ui.presenter.EventPropertiesOption;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.List;

/**
 * 动画事件属性编辑上下文：把 Presenter 数据、当前选项、运行时与控件索引封装在一起，
 * 供 {@link EventPropertySection} 按能力独立渲染。
 */
public final class EventEditContext {

	private final EventPropertiesRef ref;
	private final Timeline timeline;
	private final TimelineEditor editor;
	private final EventPropertiesPresenter presenter;
	private final AnimationEditorViewState viewState;
	private final List<EventPropertiesOption> actionOptions;
	private final List<EventPropertiesOption> animationOptions;
	private final List<EventPropertiesOption> targetOptions;
	private final String[] actionLabels;
	private final String[] animationLabels;
	private final String[] targetLabels;
	private final AnimationPropertyEditor editorHost;

	final ImInt actionIndex;
	final ImInt animationIndex;
	final ImInt targetIndex;
	final ImBoolean vfxEnabled;
	final ImBoolean inheritGroupSpatial;
	final ImInt spatialModeIndex;
	final ImBoolean stepDispatch;
	final ImInt stepStartModeIndex;
	final ImInt stepCompletionIndex;
	final ImInt pacingModeIndex;
	final ImBoolean cameraAdaptiveStep;
	final ImBoolean cameraFrustumGating;
	final ImBoolean usePhaseAnimation;

	EventEditContext(
		EventPropertiesRef ref,
		Timeline timeline,
		TimelineEditor editor,
		EventPropertiesPresenter presenter,
		AnimationEditorViewState viewState,
		List<EventPropertiesOption> actionOptions,
		List<EventPropertiesOption> animationOptions,
		List<EventPropertiesOption> targetOptions,
		String[] actionLabels,
		String[] animationLabels,
		String[] targetLabels,
		AnimationPropertyEditor editorHost
	) {
		this.ref = ref;
		this.timeline = timeline;
		this.editor = editor;
		this.presenter = presenter;
		this.viewState = viewState;
		this.actionOptions = actionOptions;
		this.animationOptions = animationOptions;
		this.targetOptions = targetOptions;
		this.actionLabels = actionLabels;
		this.animationLabels = animationLabels;
		this.targetLabels = targetLabels;
		this.editorHost = editorHost;

		this.actionIndex = new ImInt(AnimationPropertyEditor.indexOfOption(actionOptions, viewState.actionMode()));
		this.animationIndex = new ImInt(AnimationPropertyEditor.indexOfOption(animationOptions, viewState.animationId()));
		this.targetIndex = new ImInt(AnimationPropertyEditor.indexOfOption(targetOptions, viewState.targetId()));
		this.vfxEnabled = new ImBoolean(viewState.vfxEnabled());
		this.inheritGroupSpatial = new ImBoolean(viewState.inheritGroupSpatial());
		this.spatialModeIndex = new ImInt(AnimationPropertyEditor.indexOfValue(AnimationPropertyEditor.SPATIAL_MODE_VALUES, viewState.spatialMode()));
		this.stepDispatch = new ImBoolean(viewState.stepDispatch());
		this.stepStartModeIndex = new ImInt(AnimationPropertyEditor.indexOfValue(AnimationPropertyEditor.STEP_START_MODE_VALUES, viewState.stepStartMode()));
		this.stepCompletionIndex = new ImInt(AnimationPropertyEditor.indexOfValue(AnimationPropertyEditor.STEP_COMPLETION_VALUES, viewState.stepCompletionMode()));
		this.pacingModeIndex = new ImInt(AnimationPropertyEditor.indexOfValue(AnimationPropertyEditor.PACING_MODE_VALUES, viewState.pacingMode()));
		this.cameraAdaptiveStep = new ImBoolean(viewState.cameraAdaptiveStep());
		this.cameraFrustumGating = new ImBoolean(viewState.cameraFrustumGating());
		this.usePhaseAnimation = new ImBoolean(viewState.usePhaseAnimation());
		ensureInRange(this.spatialModeIndex, AnimationPropertyEditor.SPATIAL_MODE_VALUES.length);
		ensureInRange(this.pacingModeIndex, AnimationPropertyEditor.PACING_MODE_VALUES.length);
		ensureInRange(this.stepStartModeIndex, AnimationPropertyEditor.STEP_START_MODE_VALUES.length);
		ensureInRange(this.stepCompletionIndex, AnimationPropertyEditor.STEP_COMPLETION_VALUES.length);
	}

	private static void ensureInRange(ImInt index, int length) {
		if (index.get() < 0 || index.get() >= length) {
			index.set(0);
		}
	}

	public EventPropertiesRef ref() { return ref; }
	public Timeline timeline() { return timeline; }
	public TimelineEditor editor() { return editor; }
	public EventPropertiesPresenter presenter() { return presenter; }
	public AnimationEditorViewState viewState() { return viewState; }
	public List<EventPropertiesOption> actionOptions() { return actionOptions; }
	public List<EventPropertiesOption> animationOptions() { return animationOptions; }
	public List<EventPropertiesOption> targetOptions() { return targetOptions; }
	public String[] actionLabels() { return actionLabels; }
	public String[] animationLabels() { return animationLabels; }
	public String[] targetLabels() { return targetLabels; }
	public AnimationPropertyEditor editorHost() { return editorHost; }

	public String selectedActionId() {
		return actionOptions.get(actionIndex.get()).id();
	}

	public String selectedAnimationId() {
		return animationOptions.get(animationIndex.get()).id();
	}

	public String selectedTargetId() {
		return targetOptions.get(targetIndex.get()).id();
	}

	public TimelineAnimationActionMode selectedActionMode() {
		return TimelineAnimationActionMode.fromValue(selectedActionId());
	}
}
