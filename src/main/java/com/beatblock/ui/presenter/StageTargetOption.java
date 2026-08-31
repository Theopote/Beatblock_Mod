package com.beatblock.ui.presenter;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 舞台对象目标列表，供 Auto Map 目标映射 UI 使用。
 */
public record StageTargetOption(String id, String displayName) {

	public static List<StageTargetOption> fromSystem(StageObjectSystem system) {
		if (system == null) return List.of();
		List<StageTargetOption> out = new ArrayList<>();
		for (RuntimeStageObject object : system.getAll()) {
			if (object == null || object.getId() == null || object.getId().isBlank()) continue;
			String name = object.getName() != null && !object.getName().isBlank()
				? object.getName()
				: object.getId();
			out.add(new StageTargetOption(object.getId(), name + " [" + object.getId() + "]"));
		}
		out.sort(Comparator.comparing(StageTargetOption::displayName));
		return out;
	}

	public static int indexOfId(List<StageTargetOption> options, String id) {
		if (id == null || id.isBlank()) return 0;
		for (int i = 0; i < options.size(); i++) {
			if (id.equals(options.get(i).id())) return i;
		}
		return 0;
	}

	public static List<String> defaultTargetIds(List<StageTargetOption> options) {
		List<String> ids = new ArrayList<>(3);
		for (int i = 0; i < Math.min(3, options.size()); i++) {
			ids.add(options.get(i).id());
		}
		return ids;
	}
}
