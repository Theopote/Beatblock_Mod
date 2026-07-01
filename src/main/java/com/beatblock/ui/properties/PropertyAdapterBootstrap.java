package com.beatblock.ui.properties;

import com.beatblock.ui.properties.adapters.AnimationEventPropertyAdapter;
import com.beatblock.ui.properties.adapters.AudioClipPropertyAdapter;
import com.beatblock.ui.properties.adapters.BuildLayerClipPropertyAdapter;
import com.beatblock.ui.properties.adapters.CameraPropertyAdapter;
import com.beatblock.ui.properties.adapters.GlobalEventPropertyAdapter;

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
		PropertyAdapterRegistry.registerAdapter(new GlobalEventPropertyAdapter());
		PropertyAdapterRegistry.registerAdapter(new BuildLayerClipPropertyAdapter());
		PropertyAdapterRegistry.registerAdapter(new AudioClipPropertyAdapter());
		initialized = true;
	}

	static void resetForTests() {
		initialized = false;
		PropertyAdapterRegistry.clear();
	}
}
