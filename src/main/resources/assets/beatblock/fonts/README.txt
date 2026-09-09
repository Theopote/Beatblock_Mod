Fonts for BeatBlock ImGui UI.

- NotoSansSC-Regular.ttf / SimHei.ttf: CJK text fallback when no system font is found.
- BeatBlock.ttf: icon PUA glyphs.

Chinese "?" in the UI is usually a missing glyph in the ImGui atlas (not a bad zh_cn.json).
ImGuiFontManager bakes ChineseSimplifiedCommon + all characters from lang/zh_cn.json
(and en_us.json) so new preference/export strings stay visible without switching to ChineseFull
(which can overflow the atlas texture).
