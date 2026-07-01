package com.beatblock.ui.properties;

/**
 * 时间线属性适配器：根据选中对象类型渲染对应的 ImGui 属性编辑器。
 */
public interface IPropertyAdapter<T> {

	boolean renderProperties(T target);

	Class<T> getTargetType();

	default String getTitleKey() {
		return "beatblock.panel.timeline_properties";
	}

	default int getPriority() {
		return 0;
	}

	default boolean supports(Object target) {
		if (target == null) {
			return false;
		}
		Class<T> type = getTargetType();
		return type != null && type.isAssignableFrom(target.getClass());
	}
}
