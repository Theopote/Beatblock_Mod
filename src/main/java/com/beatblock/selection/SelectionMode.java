package com.beatblock.selection;

/**
 * 与 ChronoBlocks 工具栏类似：关闭时左键仅作世界拾取；点击 / 框选 为方块选择工具。
 */
public enum SelectionMode {
	/** 不拦截为选择工具 */
	OFF,
	/** 单击按操作模式选单个方块 */
	CLICK,
	/** 两次左键确定对角，按操作模式应用长方体选区 */
	BOX
}
