package com.beatblock.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * BeatBlock 主界面：手持 BeatBlock 控制器右键打开。
 */
public class BeatBlockUIScreen extends Screen {

	public BeatBlockUIScreen() {
		super(Text.translatable("gui.beatblock.title"));
	}

	@Override
	protected void init() {
		super.init();
		int centerX = width / 2;
		int y = height / 2 - 10;
		addDrawableChild(
			ButtonWidget.builder(Text.translatable("gui.beatblock.close"), b -> close())
				.dimensions(centerX - 75, y, 150, 20)
				.build()
		);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, height / 2 - 30, 0xFFFFFF);
	}
}
