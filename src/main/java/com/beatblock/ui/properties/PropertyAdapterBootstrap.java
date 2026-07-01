package com.beatblock.ui.properties;

import com.beatblock.ui.properties.adapters.AnimationEventPropertyAdapter;
import com.beatblock.ui.properties.adapters.CameraPropertyAdapter;

/**
 * 注册时间线属性适配器（对齐 Director {@code PropertyAdapterBootstrap}）。
 */
public final class PropertyAdapterBootstrap {

	private static boolean initialized;

	private PropertyAdapterBootstrap() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		PropertyAdapterRegistry.clear();
		PropertyAdapterRegistry.registerAdapter(new AnimationEventPropertyAdapter());
		PropertyAdapterRegistry.registerAdapter(new CameraPropertyAdapter());
		initialized = true;
	}

	static void resetForTests() {
		initialized = false;
		PropertyAdapterRegistry.clear();
	}
}
