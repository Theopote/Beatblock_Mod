package com.beatblock.test;

import com.beatblock.BeatBlock;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 每个测试前安装最小 {@link BeatBlockContext}，测试后清理。
 * 通过 {@code junit-platform.properties} 自动注册。
 */
public final class BeatBlockContextTestExtension implements BeforeEachCallback, AfterEachCallback {

	@Override
	public void beforeEach(ExtensionContext context) {
		BeatBlock.installContext(BeatBlockTestSupport.minimalContext());
	}

	@Override
	public void afterEach(ExtensionContext context) {
		BeatBlock.resetContext();
	}
}
