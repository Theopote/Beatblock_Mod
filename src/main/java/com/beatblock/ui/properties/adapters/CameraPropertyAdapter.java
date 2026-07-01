package com.beatblock.ui.properties.adapters;

import com.beatblock.ui.properties.editors.CameraPropertyEditor;
import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;

/**
 * 摄像机片段 / 分段 / 关键帧属性适配器。
 */
public final class CameraPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private CameraPropertyEditor editor;

	private CameraPropertyEditor editor() {
		if (editor == null) {
			editor = new CameraPropertyEditor();
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
		editor().renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
