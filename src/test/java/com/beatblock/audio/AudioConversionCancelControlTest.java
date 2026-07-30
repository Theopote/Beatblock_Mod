package com.beatblock.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioConversionCancelControlTest {

	@TempDir
	Path tempDir;

	@Test
	void cancelDestroysAttachedProcessAndCleansOutputPath() throws Exception {
		AudioConversionCancelControl control = new AudioConversionCancelControl();

		ProcessBuilder pb = new ProcessBuilder("java", "-cp", System.getProperty("java.class.path"), SleepMain.class.getName());
		Process process = pb.start();
		control.attachProcess(process);

		Path partialOutput = tempDir.resolve("partial.mp3");
		Files.createFile(partialOutput);
		control.markOutputPath(partialOutput);

		control.cancel();

		assertTrue(process.waitFor(2, TimeUnit.SECONDS), "取消后 ffmpeg 进程应在 2 秒内被终止");
		assertFalse(Files.exists(partialOutput), "取消后应删除未完成的输出文件");
		assertTrue(control.isCancelled());
	}

	@Test
	void attachProcessAfterCancelImmediatelyDestroysProcess() throws Exception {
		AudioConversionCancelControl control = new AudioConversionCancelControl();
		control.cancel();

		ProcessBuilder pb = new ProcessBuilder("java", "-cp", System.getProperty("java.class.path"), SleepMain.class.getName());
		Process process = pb.start();
		control.attachProcess(process);

		assertTrue(process.waitFor(2, TimeUnit.SECONDS), "已取消状态下 attach 的进程应立即被终止");
	}

	public static class SleepMain {
		public static void main(String[] args) throws Exception {
			Thread.sleep(60_000);
		}
	}
}
