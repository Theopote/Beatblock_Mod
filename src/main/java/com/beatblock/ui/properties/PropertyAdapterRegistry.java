package com.beatblock.ui.properties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 属性适配器注册表：按 {@link IPropertyAdapter#supports(Object)} 与 {@link IPropertyAdapter#getPriority()} 择优。
 */
public final class PropertyAdapterRegistry {

	private static final List<IPropertyAdapter<?>> ADAPTERS = new CopyOnWriteArrayList<>();

	private PropertyAdapterRegistry() {
	}

	public static <T> void registerAdapter(IPropertyAdapter<T> adapter) {
		if (adapter != null) {
			ADAPTERS.add(adapter);
		}
	}

	public static void clear() {
		ADAPTERS.clear();
	}

	@SuppressWarnings("unchecked")
	public static <T> IPropertyAdapter<T> getAdapterFor(T target) {
		if (target == null) {
			return null;
		}
		IPropertyAdapter<T> best = null;
		int bestPriority = Integer.MIN_VALUE;
		for (IPropertyAdapter<?> candidate : ADAPTERS) {
			if (!candidate.supports(target)) {
				continue;
			}
			int priority = candidate.getPriority();
			if (best == null || priority > bestPriority) {
				best = (IPropertyAdapter<T>) candidate;
				bestPriority = priority;
			}
		}
		return best;
	}

	public static List<IPropertyAdapter<?>> registeredAdapters() {
		return new ArrayList<>(ADAPTERS);
	}

	public static List<IPropertyAdapter<?>> registeredAdaptersSorted() {
		List<IPropertyAdapter<?>> copy = registeredAdapters();
		copy.sort(Comparator.comparingInt((IPropertyAdapter<?> adapter) -> adapter.getPriority()).reversed());
		return copy;
	}
}
