package com.beatblock.ui.properties.editors;

/**
 * 动画事件属性编辑器的可插拔能力块。
 * <p>
 * 未来插件动画可注册自定义 Section，扩展属性面板而不修改主编辑器。
 */
public interface EventPropertySection {

	/**
	 * 判断当前事件是否应由本 Section 渲染。
	 */
	boolean supports(EventEditContext context);

	/**
	 * 在属性面板中渲染本 Section 的控件。
	 */
	void render(EventEditContext context);
}
