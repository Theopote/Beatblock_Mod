package com.beatblock.timeline;

/**
 * 舞台对象：动画事件的目标。可对应 BlockGroup / Structure / Area / Single Block。
 * blockList 与 transform 由具体实现或 StageManager 解析。
 */
public final class StageObject {

	private final String id;
	private final StageObjectType type;
	private final String name;

	public StageObject(String id, StageObjectType type, String name) {
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
