package com.beatblock.selection;

/**
 * 与 ChronoBlocks {@code SelectionOperation} 对齐：决定点击/框选如何合并到当前选区。
 */
public enum SelectionOperation {
	NEW,
	ADD,
	SUBTRACT,
	INTERSECT
}
