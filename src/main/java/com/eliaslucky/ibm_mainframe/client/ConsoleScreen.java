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
 * one-line input.
 *
 * <p>On open, requests the current buffer from the server, which
 * replies in <em>replace</em> mode. Live pushes arrive in
 * <em>append</em> mode via
 * {@link com.eliaslucky.ibm_mainframe.network.ClientboundConsoleOutputPacket}.
 *
 * <p>Scroll-back is local: the client keeps up to
 * {@link #MAX_LOCAL_LINES} lines in memory, and the user can scroll
 * through them with the mouse wheel, arrows, Page Up/Down, or
 * Home/End. The input line stays pinned at the bottom regardless of
 * scroll position.
 */
public class ConsoleScreen extends Screen {
	private static final int CELL_W = 8;
	private static final int CELL_H = 16;
	private static final int MARGIN = 16;
	/** Local history cap. Older lines are dropped when exceeded. */
	private static final int MAX_LOCAL_LINES = 5000;
	/** Lines per mouse-wheel click. */
	private static final int WHEEL_LINES = 3;

	private static final ResourceLocation DOS_FONT = ResourceLocation.fromNamespaceAndPath("mc_dos", "ibm_vga_8x16");
	private static final Style DOS_STYLE = Style.EMPTY.withFont(DOS_FONT);

	private static final int BG		 = 0xFF000000;
	private static final int FG		 = 0xFF33FF33;	 // phosphor green
	private static final int INPUT	 = 0xFFFFFFFF;
	private static final int HINT	 = 0xFF888888;

	private static final String PROMPT = "IPL> ";

	private final BlockPos consolePos;
	private final List<String> lines = new ArrayList<>();
	private final StringBuilder input = new StringBuilder();

	/** 0 = pinned to the bottom; positive = scrolled up by that many lines. */
	private int scrollOffset = 0;

	public ConsoleScreen(BlockPos pos) {
		super(Component.literal("IBM 1052 Console"));
		this.consolePos = pos;
		ModMessages.sendToServer(new ServerboundConsoleStateRequestPacket(pos));
	}

	// --- Buffer management ---------------------------------------------

	/** Replace the whole buffer. Called on the initial state sync. */
	public void setBuffer(List<String> incoming) {
		lines.clear();
		if (incoming != null) {
			for (String entry : incoming) {
				if (entry == null) continue;
				// Split multi-line entries so scroll indexing is line-based.
				for (String s : entry.split("\n", -1)) lines.add(s);
			}
		}
		trimLocal();
		scrollOffset = 0;
	}

	/** Append one or more lines. Called on live pushes. */
	public void appendLine(String line) {
		if (line == null) return;
		for (String l : line.split("\n", -1)) lines.add(l);
		trimLocal();
		// If the user is scrolled up, leave them there. If pinned to the
		// bottom, they continue to see new lines as they arrive.
	}

	private void trimLocal() {
		while (lines.size() > MAX_LOCAL_LINES) lines.remove(0);
	}

	// --- Rendering -----------------------------------------------------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, this.width, this.height, BG);

		int inputY = this.height - CELL_H * 2;
		int usableHeight = inputY - MARGIN;
		int maxVisible = Math.max(1, usableHeight / CELL_H);

		int total = lines.size();
		int maxScroll = Math.max(0, total - maxVisible);
		if (scrollOffset > maxScroll) scrollOffset = maxScroll;
		if (scrollOffset < 0) scrollOffset = 0;

		int endIndex = total - scrollOffset;
		int startIndex = Math.max(0, endIndex - maxVisible);

		int y = MARGIN;
		for (int i = startIndex; i < endIndex; i++) {
			drawDos(g, lines.get(i), MARGIN, y, FG);
			y += CELL_H;
		}

		// Scroll indicator, right-aligned in the top row.
		if (scrollOffset > 0) {
			String indicator = "^ " + scrollOffset + " above";
			int ix = this.width - MARGIN - indicator.length() * CELL_W;
			drawDos(g, indicator, ix, MARGIN, HINT);
		}

		// Input line, always at the bottom.
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

	// --- Input ---------------------------------------------------------

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
				// Snap to bottom so the user sees the response.
				scrollOffset = 0;
				// Local echo: immediate feedback. The server must NOT
				// also echo, or the line appears twice. See the note in
				// ServerboundConsoleCommandPacket.
				lines.add(PROMPT + cmd);
				trimLocal();
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

			case GLFW.GLFW_KEY_PAGE_UP -> {
				scrollOffset += visibleLineCount();
				clampScroll();
				return true;
			}
			case GLFW.GLFW_KEY_PAGE_DOWN -> {
				scrollOffset -= visibleLineCount();
				clampScroll();
				return true;
			}
			case GLFW.GLFW_KEY_UP -> {
				scrollOffset += 1;
				clampScroll();
				return true;
			}
			case GLFW.GLFW_KEY_DOWN -> {
				scrollOffset -= 1;
				clampScroll();
				return true;
			}
			case GLFW.GLFW_KEY_HOME -> {
				scrollOffset = Integer.MAX_VALUE;
				clampScroll();
				return true;
			}
			case GLFW.GLFW_KEY_END -> {
				scrollOffset = 0;
				return true;
			}
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		// Sign convention: positive delta = scroll up in vanilla.
		scrollOffset -= (int) Math.signum(delta) * WHEEL_LINES;
		clampScroll();
		return true;
	}

	private int visibleLineCount() {
		int inputY = this.height - CELL_H * 2;
		int usableHeight = inputY - MARGIN;
		return Math.max(1, usableHeight / CELL_H);
	}

	private void clampScroll() {
		int maxScroll = Math.max(0, lines.size() - visibleLineCount());
		if (scrollOffset > maxScroll) scrollOffset = maxScroll;
		if (scrollOffset < 0) scrollOffset = 0;
	}

	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean shouldCloseOnEsc() { return true; }
}
