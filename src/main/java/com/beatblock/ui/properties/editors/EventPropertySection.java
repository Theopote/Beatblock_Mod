package com.beatblock.ui.properties.editors;

/**
 * 动画事件属性编辑器的可插拔能力块。
 * <p>
 * 主编辑器按 {@link Tab} 分组渲染；插件可 {@link EventPropertySectionRegistry#register} 扩展面板，
 * 无需修改 {@link AnimationPropertyEditor}。
 */
public interface EventPropertySection {

	/** Property panel tab the section belongs to. */
	enum Tab {
		BASIC,
		SPATIAL,
		ADVANCED,
		INFO
	}

	/**
	 * Tab this section renders under. Default {@link Tab#BASIC}.
	 */
	default Tab tab() {
		return Tab.BASIC;
	}

	/**
	 * Sort key within a tab (lower first). Default 100.
	 */
	default int order() {
		return 100;
	}

	/**
	 * Whether this section should render for the current event/context.
	 */
	boolean supports(EventEditContext context);

	/**
	 * Render ImGui controls for this section.
	 */
	void render(EventEditContext context);
}
