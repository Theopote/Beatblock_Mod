package com.beatblock.engine.layer;

/**
 * 建造图层分组：仅 UI / 编排元数据，不改变方块归属逻辑。
 */
public final class BuildLayerGroup {

	private final String id;
	private String name;
	private int colorArgb;

	public BuildLayerGroup(String id, String name, int colorArgb) {
		this.id = id != null ? id : "";
		this.name = name != null && !name.isBlank() ? name.trim() : this.id;
		this.colorArgb = colorArgb;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		if (name != null && !name.isBlank()) {
			this.name = name.trim();
		}
	}

	public int getColorArgb() {
		return colorArgb;
	}

	public void setColorArgb(int colorArgb) {
		this.colorArgb = colorArgb;
	}
}
