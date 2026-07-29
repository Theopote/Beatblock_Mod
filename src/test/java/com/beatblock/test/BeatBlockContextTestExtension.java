package com.beatblock.test;

import com.beatblock.BeatBlock;
import com.beatblock.selection.BeatBlockSelectionManager;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.util.UUID;

/**
 * 每个测试前安装最小 {@link BeatBlockContext}，测试后清理。
 * 通过 {@code junit-platform.properties} 自动注册。
 */
public final class BeatBlockContextTestExtension implements BeforeEachCallback, AfterEachCallback {

	@Override
	public void beforeEach(ExtensionContext context) {
		Path testConfigDir = Path.of("build", "tmp", "test-config", UUID.randomUUID().toString());
		System.setProperty("beatblock.test.configDir", testConfigDir.toAbsolutePath().toString());
		BeatBlockSelectionManager.get().reset();
		BeatBlock.installContext(BeatBlockTestSupport.minimalContext());
	}

	@Override
	public void afterEach(ExtensionContext context) {
		BeatBlockSelectionManager.get().reset();
		BeatBlock.resetContext();
		System.clearProperty("beatblock.test.configDir");
	}
}
