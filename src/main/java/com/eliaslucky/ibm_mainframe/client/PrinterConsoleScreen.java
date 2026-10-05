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
 * The IBM 1052 Printer-Keyboard's "display": a continuous form
 * printout.
 *
 * <p>Sprocket holes run down the left and right margins, one per
 * text row, making it look like the physical fan-fold paper the 1052 used.
 */
public class PrinterConsoleScreen extends Screen {
	// Cell geometry
	private static final int CELL_W = 8;
	private static final int CELL_H = 16;

	// Paper layout
	private static final int SPROCKET_STRIP_W = 20;  // width of each edge strip
	private static final int TOP_MARGIN		 = 20;
	private static final int BOTTOM_MARGIN	 = 20;
	private static final int LEFT_PAD		 = SPROCKET_STRIP_W + CELL_W;
	private static final int RIGHT_PAD		 = SPROCKET_STRIP_W + CELL_W;

	private static final int MAX_LOCAL_LINES = 5000;
	private static final int WHEEL_LINES	 = 3;

	// Palette
	private static final int PAPER_BG	   = 0xFFEFE8D4;  // cream
	private static final int PAPER_STRIP   = 0xFFD4CBAF;  // slightly darker edge
	private static final int PAPER_EDGE    = 0xFFB8AE8E;  // shadow between strips
	private static final int SPROCKET_HOLE = 0xFF6E6754;  // punch holes
	private static final int TEXT_INK	   = 0xFF1A1A44;  // dark blue-black
	private static final int PROMPT_INK    = 0xFF2A2A5A;
	private static final int PRINT_HEAD    = 0xFF3A3A6A;
	private static final int HINT_INK	   = 0xFF8A8570;

	private static final ResourceLocation DOS_FONT = ResourceLocation.fromNamespaceAndPath("mc_dos", "ibm_vga_8x16");
	private static final Style DOS_STYLE = Style.EMPTY.withFont(DOS_FONT);

	/** The 1052's prompt is a bare asterisk, like a real console. */
	private static final String PROMPT = "* ";

	private final BlockPos consolePos;
	private final List<String> lines = new ArrayList<>();
	private final StringBuilder input = new StringBuilder();
	private int scrollOffset = 0;

	public PrinterConsoleScreen(BlockPos pos) {
		super(Component.literal("IBM 1052 Printer-Keyboard"));
		this.consolePos = pos;
		ModMessages.sendToServer(new ServerboundConsoleStateRequestPacket(pos));
	}

	// --- Buffer --------------------------------------------------------

	public void setBuffer(List<String> incoming) {
		lines.clear();
		if (incoming != null) {
			for (String entry : incoming) {
				if (entry == null) continue;
				for (String s : entry.split("\n", -1)) lines.add(s);
			}
		}
		trimLocal();
		scrollOffset = 0;
	}

	public void appendLine(String line) {
		if (line == null) return;
		for (String l : line.split("\n", -1)) lines.add(l);
		trimLocal();
	}

	private void trimLocal() {
		while (lines.size() > MAX_LOCAL_LINES) lines.remove(0);
	}

	// --- Rendering -----------------------------------------------------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// Paper
		g.fill(0, 0, this.width, this.height, PAPER_BG);

		// Sprocket strips on the left and right.
		g.fill(0, 0, SPROCKET_STRIP_W, this.height, PAPER_STRIP);
		g.fill(this.width - SPROCKET_STRIP_W, 0, this.width, this.height, PAPER_STRIP);

		// Shadows between the strips and the paper.
		g.fill(SPROCKET_STRIP_W, 0, SPROCKET_STRIP_W + 1, this.height, PAPER_EDGE);
		g.fill(this.width - SPROCKET_STRIP_W - 1, 0,
			   this.width - SPROCKET_STRIP_W, this.height, PAPER_EDGE);

		drawSprockets(g);

		// Content area.
		int contentLeft   = LEFT_PAD;
		int contentRight  = this.width - RIGHT_PAD;
		int contentTop	  = TOP_MARGIN;
		int inputY		  = this.height - BOTTOM_MARGIN - CELL_H;
		int maxVisible	  = Math.max(1, (inputY - contentTop) / CELL_H);

		// Faint ruled lines help the eye track across the page.
		for (int i = 1; i < maxVisible; i++) {
			int y = contentTop + i * CELL_H - 1;
			g.fill(contentLeft, y, contentRight, y + 1, 0x18A89E7F);
		}

		// Scroll math.
		int total = lines.size();
		int maxScroll = Math.max(0, total - maxVisible);
		if (scrollOffset > maxScroll) scrollOffset = maxScroll;
		if (scrollOffset < 0) scrollOffset = 0;

		int endIndex = total - scrollOffset;
		int startIndex = Math.max(0, endIndex - maxVisible);

		int y = contentTop;
		for (int i = startIndex; i < endIndex; i++) {
			drawInk(g, lines.get(i), contentLeft, y, TEXT_INK);
			y += CELL_H;
		}

		// Scroll hint, right-aligned above the input.
		if (scrollOffset > 0) {
			String indicator = "^ " + scrollOffset + " lines";
			int ix = contentRight - indicator.length() * CELL_W;
			drawInk(g, indicator, ix, TOP_MARGIN - CELL_H, HINT_INK);
		}

		// Input line.
		String inputLine = PROMPT + input;
		drawInk(g, inputLine, contentLeft, inputY, PROMPT_INK);

		int markerX = contentLeft + inputLine.length() * CELL_W;
		int markerY = inputY + CELL_H - 4;
		g.fill(markerX, markerY, markerX + 3, markerY + 2, PRINT_HEAD);
	}

	private void drawSprockets(GuiGraphics g) {
		int holeW = 5;
		int holeH = 5;
		int leftX  = SPROCKET_STRIP_W / 2 - holeW / 2;
		int rightX = this.width - SPROCKET_STRIP_W / 2 - holeW / 2;

		for (int rowY = CELL_H / 2; rowY < this.height; rowY += CELL_H) {
			g.fill(leftX, rowY, leftX + holeW, rowY + holeH, SPROCKET_HOLE);
			g.fill(rightX, rowY, rightX + holeW, rowY + holeH, SPROCKET_HOLE);
		}
	}

	private void drawInk(GuiGraphics g, String text, int x, int y, int color) {
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
		if (cp >= 32 && cp != 127) { input.append(cp); return true; }
		return super.charTyped(cp, mods);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		switch (key) {
			case GLFW.GLFW_KEY_ESCAPE -> { onClose(); return true; }

			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				String cmd = input.toString();
				input.setLength(0);
				scrollOffset = 0;
				// Local echo.
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

			case GLFW.GLFW_KEY_PAGE_UP	 -> { scrollOffset += visibleLineCount(); clampScroll(); return true; }
			case GLFW.GLFW_KEY_PAGE_DOWN -> { scrollOffset -= visibleLineCount(); clampScroll(); return true; }
			case GLFW.GLFW_KEY_UP		 -> { scrollOffset += 1; clampScroll(); return true; }
			case GLFW.GLFW_KEY_DOWN		 -> { scrollOffset -= 1; clampScroll(); return true; }
			case GLFW.GLFW_KEY_HOME		 -> { scrollOffset = Integer.MAX_VALUE; clampScroll(); return true; }
			case GLFW.GLFW_KEY_END		 -> { scrollOffset = 0; return true; }
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		scrollOffset -= (int) Math.signum(delta) * WHEEL_LINES;
		clampScroll();
		return true;
	}

	private int visibleLineCount() {
		int inputY = this.height - BOTTOM_MARGIN - CELL_H;
		return Math.max(1, (inputY - TOP_MARGIN) / CELL_H);
	}

	private void clampScroll() {
		int maxScroll = Math.max(0, lines.size() - visibleLineCount());
		if (scrollOffset > maxScroll) scrollOffset = maxScroll;
		if (scrollOffset < 0) scrollOffset = 0;
	}

	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean shouldCloseOnEsc() { return true; }
}
