package com.beatblock.client.export;

import java.util.function.Consumer;

/** 客户端主线程调度入口，由 {@link com.beatblock.BeatBlockClient} 安装。 */
public final class ClientThreadExecutor {

	private static Consumer<Runnable> delegate = Runnable::run;

	private ClientThreadExecutor() {}

	public static void install(Consumer<Runnable> executor) {
		delegate = executor != null ? executor : Runnable::run;
	}

	public static void run(Runnable task) {
		if (task != null) {
			delegate.accept(task);
		}
	}
}
