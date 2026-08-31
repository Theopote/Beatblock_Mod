package com.beatblock.timeline;

/**
 * 时间轴侧舞台对象引用：动画事件的目标元数据（id / 类型 / 显示名）。
 * 可对应 BlockGroup / Structure / Area / Single Block。
 * <p>
 * 运行时方块集合见 {@link com.beatblock.engine.RuntimeStageObject}。
 */
public final class StageObjectRef {

	private final String id;
	private final StageObjectType type;
	private final String name;

	public StageObjectRef(String id, StageObjectType type, String name) {
		this.id = id != null ? id : "";
		this.type = type != null ? type : StageObjectType.SINGLE_BLOCK;
		this.name = name != null ? name : id;
	}

	public String getId() {
		return id;
	}

	public StageObjectType getType() {
		return type;
	}

	public String getName() {
		return name;
	}
}
