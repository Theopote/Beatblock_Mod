package com.beatblock.audio;

/** 将异步服务产生的回调交给拥有客户端状态的线程执行。 */
@FunctionalInterface
public interface MainThreadDispatcher {

	void execute(Runnable action);

	static MainThreadDispatcher immediate() {
		return Runnable::run;
	}
}
