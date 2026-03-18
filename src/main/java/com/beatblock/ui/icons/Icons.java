package com.beatblock.ui.icons;

/**
 * BeatBlock UI Icons (BeatBlock.ttf).
 * <p>
 * This mod uses a custom icon font and references icons via PUA codepoints (U+Fxxx),
 * instead of relying on system emoji rendering.
 */
public final class Icons {
	private Icons() {
		throw new AssertionError("Cannot instantiate Icons");
	}

	// 以下 codepoint 来自 demo.html 的 glyph 列表（BeatBlock.ttf / IcoMoon 生成）。
	// demo.html 内的 icon-bb-* -> 对应 U+Fxxx。
	public static final String VISIBLE = "\uF067";      // icon-bb-visible
	public static final String LOCK = "\uF095";         // icon-bb-lock
	public static final String CHECK = "\uF054";        // icon-bb-check
	public static final String MORE_HORIZ = "\uF06B";   // icon-bb-more-horiz (用于 ☰)
	public static final String NOTE = "\uF04D";         // icon-bb-note (用于 ♪)

	// 与现有 UI 命名保持兼容：语义化别名
	public static final String EYE = VISIBLE;            // 可见图标
	public static final String MENU = MORE_HORIZ;       // 菜单/拖拽提示图标
	public static final String MUSIC_NOTE = NOTE;       // 音符图标
}

