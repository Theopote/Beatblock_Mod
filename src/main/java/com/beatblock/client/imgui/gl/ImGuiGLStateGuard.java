package com.beatblock.client.imgui.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;

/**
 * ImGui 渲染前的 GL 状态保护：修复 Minecraft 1.21+ 下 sampler/color mask 等污染导致的“全黑无内容”。
 * 参考 MasterPlanner / NodeCraft：在 renderDrawData 前强制 ColorMask、解绑 Sampler/Texture、混合状态。
 */
public final class ImGuiGLStateGuard implements AutoCloseable {
	private final int prevActiveTexture;
	private final int prevSampler0;
	private final int prevTex2d0;
	private final boolean[] prevColorMask = new boolean[4];
	private final boolean prevDepthTest;
	private final boolean prevBlend;
	private final boolean prevScissor;

	private ImGuiGLStateGuard() {
		RenderSystem.assertOnRenderThread();

		prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		prevSampler0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
		prevTex2d0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

		ByteBuffer cm = BufferUtils.createByteBuffer(4);
		GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, cm);
		prevColorMask[0] = cm.get(0) != 0;
		prevColorMask[1] = cm.get(1) != 0;
		prevColorMask[2] = cm.get(2) != 0;
		prevColorMask[3] = cm.get(3) != 0;
		prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
		prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

		applyImGuiSafeState();
	}

	public static ImGuiGLStateGuard enter() {
		return new ImGuiGLStateGuard();
	}

	private static void applyImGuiSafeState() {
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL33.glBindSampler(0, 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		GL11.glColorMask(true, true, true, true);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	@Override
	public void close() {
		RenderSystem.assertOnRenderThread();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL33.glBindSampler(0, prevSampler0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2d0);
		GL11.glColorMask(prevColorMask[0], prevColorMask[1], prevColorMask[2], prevColorMask[3]);
		setEnabled(GL11.GL_DEPTH_TEST, prevDepthTest);
		setEnabled(GL11.GL_BLEND, prevBlend);
		setEnabled(GL11.GL_SCISSOR_TEST, prevScissor);
		GL13.glActiveTexture(prevActiveTexture);
	}

	private static void setEnabled(int cap, boolean enabled) {
		if (enabled) GL11.glEnable(cap);
		else GL11.glDisable(cap);
	}
}
