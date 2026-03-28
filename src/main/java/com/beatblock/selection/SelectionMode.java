package com.beatblock.selection;

/**
 * 与 ChronoBlocks 类工具栏对齐的方块选择方式：关闭时左键仅作世界拾取；其余为各类选区工具。
 */
public enum SelectionMode {
	/** 不拦截为选择工具 */
	OFF,
	/** 单击按操作模式选单个方块 */
	CLICK,
	/** 两次左键对角 + 移动预览，长方体选区 */
	BOX,
	/** 两次左键端点 + 移动预览，沿体素路径选方块 */
	LINE,
	/** 单击中心，按球半径（属性面板）选欧氏球内方块 */
	SPHERE,
	/** 单击起点，六邻域连通且方块（状态）一致，魔棒 */
	CONNECTED,
	/** 单击一格，选中同 XZ 整列（维度高度范围） */
	COLUMN
}
