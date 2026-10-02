package com.eliaslucky.ibm_mainframe.client;

import com.eliaslucky.ibm_mainframe.network.ModMessages;
import com.eliaslucky.ibm_mainframe.network.ServerboundConsoleCommandPacket;
import com.eliaslucky.ibm_mainframe.network.ServerboundConsoleStateRequestPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The IBM 1052's display: a scroll of printed lines, a prompt, and a
 * one-line input. Nothing more — real consoles didn't have graphics.
 *
 * <p>On open, requests the current buffer from the server. On Enter,
 * sends the input line to the console BE. The server pushes new
 * output via {@link com.eliaslucky.ibm_mainframe.network.ClientboundConsoleOutputPacket}.
 */
public class ConsoleScreen extends Screen {
	private static final int CELL_W = 8;
	private static final int CELL_H = 16;
	private static final int MARGIN = 16;

	private static final ResourceLocation DOS_FONT =
			ResourceLocation.fromNamespaceAndPath("mc_dos", "ibm_vga_8x16");
	private static final Style DOS_STYLE = Style.EMPTY.withFont(DOS_FONT);

	private static final int BG		= 0xFF000000;
	private static final int FG		= 0xFF33FF33;	// phosphor green
	private static final int INPUT	= 0xFFFFFFFF;

	private static final String PROMPT = "IPL> ";

	private final BlockPos consolePos;
	private final List<String> lines = new ArrayList<>();
	private final StringBuilder input = new StringBuilder();

	public ConsoleScreen(BlockPos pos) {
		super(Component.literal("IBM 1052 Console"));
		this.consolePos = pos;
		ModMessages.sendToServer(new ServerboundConsoleStateRequestPacket(pos));
	}

	// Called by the clientbound packet when the buffer arrives.
	public void setBuffer(List<String> incoming) {
		lines.clear();
		lines.addAll(incoming);
	}

	public void appendLine(String line) {
		for (String l : line.split("\n", -1)) lines.add(l);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, this.width, this.height, BG);

		int maxVisible = Math.max(1, (this.height - CELL_H * 3) / CELL_H);
		int start = Math.max(0, lines.size() - maxVisible);

		int y = MARGIN;
		for (int i = start; i < lines.size(); i++) {
			drawDos(g, lines.get(i), MARGIN, y, FG);
			y += CELL_H;
		}

		// Input line
		int inputY = this.height - CELL_H * 2;
		String cursor = ((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : " ";
		drawDos(g, PROMPT + input + cursor, MARGIN, inputY, INPUT);
	}

	private void drawDos(GuiGraphics g, String text, int x, int y, int color) {
		if (text == null || text.isEmpty()) return;
		var mcFont = Minecraft.getInstance().font;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == ' ') continue;
			g.drawString(mcFont,
					Component.literal(String.valueOf(c)).withStyle(DOS_STYLE),
					x + i * CELL_W, y, color, false);
		}
	}

	@Override
	public boolean charTyped(char cp, int mods) {
		if (cp >= 32 && cp != 127) {
			input.append(cp);
			return true;
		}
		return super.charTyped(cp, mods);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		switch (key) {
			case GLFW.GLFW_KEY_ESCAPE -> { onClose(); return true; }
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				String cmd = input.toString();
				input.setLength(0);
				// Echo locally; the server will also echo.
				lines.add(PROMPT + cmd);
				if (!cmd.isBlank()) {
					ModMessages.sendToServer(
							new ServerboundConsoleCommandPacket(consolePos, cmd));
				}
				return true;
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (input.length() > 0) input.deleteCharAt(input.length() - 1);
				return true;
			}
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean shouldCloseOnEsc() { return true; }
}
