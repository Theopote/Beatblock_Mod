package com.beatblock.ui.properties.adapters;

import com.beatblock.BeatBlock;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.ui.properties.editors.CameraPropertyEditor;

/**
 * 摄像机片段 / 分段 / 关键帧属性适配器。
 */
public final class CameraPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private CameraPropertyEditor editor;
	private EventPropertiesPresenter boundPresenter;

	private CameraPropertyEditor editor(EventPropertiesPresenter presenter) {
		if (editor == null || boundPresenter != presenter) {
			editor = presenter != null
				? new CameraPropertyEditor(presenter, BeatBlock::getContext)
				: new CameraPropertyEditor();
			boundPresenter = presenter;
		}
		return editor;
	}

	@Override
	public Class<TimelinePropertyContext> getTargetType() {
		return TimelinePropertyContext.class;
	}

	@Override
	public int getPriority() {
		return 90;
	}

	@Override
	public String getTitleKey() {
		return "beatblock.camera.title";
	}

	@Override
	public boolean supports(Object target) {
		return target instanceof TimelinePropertyContext ctx && TimelinePropertyKinds.isCameraRef(ctx.ref());
	}

	@Override
	public boolean renderProperties(TimelinePropertyContext ctx) {
		editor(ctx.presenter()).renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
