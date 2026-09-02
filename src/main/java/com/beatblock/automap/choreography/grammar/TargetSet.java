package com.beatblock.automap.choreography.grammar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 短语参与的舞台对象 id 列表（有序）。 */
public record TargetSet(List<String> objectIds) {

	public TargetSet {
		objectIds = objectIds != null ? List.copyOf(objectIds) : List.of();
	}

	public static TargetSet of(String... ids) {
		if (ids == null || ids.length == 0) return new TargetSet(List.of());
		List<String> out = new ArrayList<>(ids.length);
		for (String id : ids) {
			if (id != null && !id.isBlank()) out.add(id);
		}
		return new TargetSet(out);
	}

	public static TargetSet unique(List<String> ids) {
		if (ids == null || ids.isEmpty()) return new TargetSet(List.of());
		Set<String> seen = new LinkedHashSet<>();
		for (String id : ids) {
			if (id != null && !id.isBlank()) seen.add(id);
		}
		return new TargetSet(new ArrayList<>(seen));
	}

	public boolean isEmpty() {
		return objectIds.isEmpty();
	}

	public int size() {
		return objectIds.size();
	}
}
