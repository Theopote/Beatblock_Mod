package com.beatblock.audio;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 分析任务进程生命周期控制（依赖安装与分析子进程共享）。
 */
public final class AnalysisCancelControl {

	private final AtomicReference<Process> activeProcess = new AtomicReference<>();
	private volatile boolean cancelled;

	public void attachProcess(Process process) {
		if (process == null) return;
		activeProcess.set(process);
		if (cancelled) {
			process.destroyForcibly();
		}
	}

	public void clearProcess(Process process) {
		activeProcess.compareAndSet(process, null);
	}

	public void cancelRunningProcess() {
		cancelled = true;
		Process process = activeProcess.getAndSet(null);
		if (process != null) {
			process.destroyForcibly();
		}
	}

	public boolean isCancelled() {
		return cancelled;
	}
}
