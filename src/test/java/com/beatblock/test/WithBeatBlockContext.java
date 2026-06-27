package com.beatblock.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为依赖 {@link com.beatblock.BeatBlock#getContext()} 的测试安装最小 Context。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(BeatBlockContextTestExtension.class)
public @interface WithBeatBlockContext {
}
