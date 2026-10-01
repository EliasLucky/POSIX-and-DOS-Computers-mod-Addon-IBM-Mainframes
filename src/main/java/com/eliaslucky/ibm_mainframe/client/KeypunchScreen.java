package com.eliaslucky.ibm_mainframe.client;

import com.eliaslucky.ibm_mainframe.network.ModMessages;
import com.eliaslucky.ibm_mainframe.network.ServerboundPunchDeckPacket;

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
 * The front panel of an IBM 029 Keypunch.
 *
 * <p>This is not a terminal and does not speak to a computer. It is a
 * standalone card punch: the user types lines, each line becomes one
 * 80-column card, and pressing F5 turns the buffer into a deck the
 * server delivers as an item.
 *
 * <p>The layout mirrors the real machine's card path: a column ruler
 * across the top where the card's printed scale would be, a working
 * area of up to 80 columns per line, and a status strip at the bottom.
 * The cursor is a filled block, as on the real 029 the punch position
 * indicator moved one column at a time.
 */
public class KeypunchScreen extends Screen {
	// Card geometry
	private static final int MAX_COLUMNS = 80;
	private static final int MAX_CARDS	 = 2000;

	// Screen chrome (matching the DOS font cells)
	private static final int CELL_W = 8;
	private static final int CELL_H = 16;
	private static final int MARGIN = 16;

	private static final ResourceLocation DOS_FONT = ResourceLocation.fromNamespaceAndPath("mc_dos", "ibm_vga_8x16");
	private static final Style DOS_STYLE = Style.EMPTY.withFont(DOS_FONT);

	// Colors
	private static final int BG			= 0xFF000000;
	private static final int TITLE_BG	= 0xFFAAAAAA;
	private static final int TITLE_FG	= 0xFF000000;
	private static final int RULER_FG	= 0xFF00AAAA;
	private static final int CARD_NUM	= 0xFF555555;
	private static final int CARD_FG	= 0xFFAAAAAA;
	private static final int CURSOR_FG	= 0xFF000000;
	private static final int STATUS_BG	= 0xFF555555;
	private static final int STATUS_FG	= 0xFFFFFFFF;
	private static final int MESSAGE_FG = 0xFF55FF55;

	private final BlockPos keypunchPos;
	private final List<StringBuilder> lines = new ArrayList<>();
	private int cursorRow = 0;
	private int cursorCol = 0;
	private int scrollRow = 0;
	private String statusMessage = "";

	public KeypunchScreen(BlockPos pos) {
		super(Component.literal("IBM 029 Keypunch"));
		this.keypunchPos = pos;
		this.lines.add(new StringBuilder());
	}

	// --- Layout helpers ----------------------------------------------------

	private int rulerRow()	{ return 2; }
	private int textTop()	{ return 3; }
	private int textLeft()	{ return MARGIN + CELL_W * 5; }  // leave room for card numbers
	private int visibleRows() {
		return Math.max(1, (this.height - textTop() * CELL_H - CELL_H * 2) / CELL_H);
	}

	// --- Rendering ---------------------------------------------------------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, this.width, this.height, BG);

		drawTitleBar(g);
		drawRuler(g);
		drawCards(g);
		drawCursor(g);
		drawStatusStrip(g);
	}

	private void drawTitleBar(GuiGraphics g) {
		g.fill(0, 0, this.width, CELL_H, TITLE_BG);
		String title = " IBM 029 KEYPUNCH -- 80 COLUMN CARD ";
		drawDos(g, title, MARGIN, 0, TITLE_FG);
	}

	/**
	 * The ruler sits where the printed scale on a real card would be.
	 * Every tenth column shows its leading digit; every fifth shows a
	 * tick; the rest are dots.
	 */
	private void drawRuler(GuiGraphics g) {
		StringBuilder sb = new StringBuilder(MAX_COLUMNS);
		for (int c = 0; c < MAX_COLUMNS; c++) {
			int col = c + 1;
			if (col == 1)				 sb.append('1');
			else if (col % 10 == 0)		 sb.append((char) ('0' + (col / 10) % 10));
			else if (col % 5 == 0)		 sb.append('+');
			else						 sb.append('.');
		}
		drawDos(g, sb.toString(), textLeft(), rulerRow() * CELL_H, RULER_FG);
	}

	private void drawCards(GuiGraphics g) {
		int visible = visibleRows();
		for (int i = 0; i < visible; i++) {
			int li = scrollRow + i;
			if (li >= lines.size()) break;
			int y = (textTop() + i) * CELL_H;

			// Card number in the left margin, right-aligned.
			String num = String.format("%3d", li + 1);
			drawDos(g, num, MARGIN, y, CARD_NUM);

			String content = lines.get(li).toString();
			drawDos(g, content, textLeft(), y, CARD_FG);
		}
	}

	private void drawCursor(GuiGraphics g) {
		if ((System.currentTimeMillis() / 500) % 2 != 0) return;

		int cr = cursorRow - scrollRow;
		if (cr < 0 || cr >= visibleRows()) return;

		int cx = textLeft() + cursorCol * CELL_W;
		int cy = (textTop() + cr) * CELL_H;

		// Filled block cursor.
		g.fill(cx, cy, cx + CELL_W, cy + CELL_H, CARD_FG);

		// Redraw the character underneath in the inverse color.
		String line = lines.get(cursorRow).toString();
		if (cursorCol < line.length()) {
			char c = line.charAt(cursorCol);
			if (c != ' ') {
				drawDos(g, String.valueOf(c), cx, cy, CURSOR_FG);
			}
		}
	}

	private void drawStatusStrip(GuiGraphics g) {
		int y = this.height - CELL_H * 2;
		g.fill(0, y, this.width, y + CELL_H, STATUS_BG);

		String hints = " F5=Punch deck	 Tab=Column   Esc=Cancel   Arrows=Move ";
		drawDos(g, hints, MARGIN, y, STATUS_FG);

		if (statusMessage != null && !statusMessage.isEmpty()) {
			int msgY = this.height - CELL_H;
			drawDos(g, statusMessage, MARGIN, msgY, MESSAGE_FG);
		}
	}

	/**
	 * Draw a string using the DOS font, cell by cell. Spaces are
	 * skipped — they cost nothing and the cell math handles them.
	 */
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

	// --- Input -------------------------------------------------------------

	@Override
	public boolean charTyped(char cp, int mods) {
		if (cp < 32 || cp == 127) return super.charTyped(cp, mods);
		if (cursorCol >= MAX_COLUMNS) {
			statusMessage = "Card column " + MAX_COLUMNS + " is the last.";
			return true;
		}
		lines.get(cursorRow).insert(cursorCol, cp);
		cursorCol++;
		statusMessage = "";
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_ESCAPE -> { this.onClose(); return true; }

			case GLFW.GLFW_KEY_F5 -> { punch(); return true; }

			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				splitLine();
				return true;
			}
			case GLFW.GLFW_KEY_TAB -> { insertTab(); return true; }
			case GLFW.GLFW_KEY_BACKSPACE -> { backspace(); return true; }
			case GLFW.GLFW_KEY_DELETE -> { deleteForward(); return true; }

			case GLFW.GLFW_KEY_LEFT -> { moveCol(-1); return true; }
			case GLFW.GLFW_KEY_RIGHT -> { moveCol(1); return true; }
			case GLFW.GLFW_KEY_UP -> { moveRow(-1); return true; }
			case GLFW.GLFW_KEY_DOWN -> { moveRow(1); return true; }
			case GLFW.GLFW_KEY_HOME -> { cursorCol = 0; return true; }
			case GLFW.GLFW_KEY_END -> {
				cursorCol = lines.get(cursorRow).length();
				return true;
			}
			case GLFW.GLFW_KEY_PAGE_UP -> {
				scrollRow = Math.max(0, scrollRow - visibleRows());
				return true;
			}
			case GLFW.GLFW_KEY_PAGE_DOWN -> {
				scrollRow += visibleRows();
				clampScroll();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// --- Editing -----------------------------------------------------------

	private void moveCol(int d) {
		int len = lines.get(cursorRow).length();
		cursorCol = Math.max(0, Math.min(len, cursorCol + d));
	}

	private void moveRow(int d) {
		int next = Math.max(0, Math.min(lines.size() - 1, cursorRow + d));
		if (next != cursorRow) {
			cursorRow = next;
			cursorCol = Math.min(cursorCol, lines.get(cursorRow).length());
			clampScroll();
		}
	}

	private void insertTab() {
		// Column ruler has ticks every 5, so 5-column stops are natural.
		int stop = 5;
		int pad = stop - (cursorCol % stop);
		for (int i = 0; i < pad && cursorCol < MAX_COLUMNS; i++) {
			lines.get(cursorRow).insert(cursorCol++, ' ');
		}
	}

	private void splitLine() {
		if (lines.size() >= MAX_CARDS) {
			statusMessage = "Deck is at its maximum of " + MAX_CARDS + " cards.";
			return;
		}
		StringBuilder cur = lines.get(cursorRow);
		String rest = cur.substring(cursorCol);
		cur.setLength(cursorCol);
		lines.add(cursorRow + 1, new StringBuilder(rest));
		cursorRow++;
		cursorCol = 0;
		clampScroll();
	}

	private void backspace() {
		StringBuilder cur = lines.get(cursorRow);
		if (cursorCol > 0) {
			cur.deleteCharAt(cursorCol - 1);
			cursorCol--;
		} else if (cursorRow > 0) {
			StringBuilder prev = lines.get(cursorRow - 1);
			int join = prev.length();
			prev.append(cur);
			lines.remove(cursorRow);
			cursorRow--;
			cursorCol = join;
			clampScroll();
		}
	}

	private void deleteForward() {
		StringBuilder cur = lines.get(cursorRow);
		if (cursorCol < cur.length()) {
			cur.deleteCharAt(cursorCol);
		} else if (cursorRow < lines.size() - 1) {
			cur.append(lines.get(cursorRow + 1));
			lines.remove(cursorRow + 1);
		}
	}

	private void clampScroll() {
		int visible = visibleRows();
		if (cursorRow < scrollRow) scrollRow = cursorRow;
		if (cursorRow >= scrollRow + visible) scrollRow = cursorRow - visible + 1;
		scrollRow = Math.max(0, Math.min(scrollRow,
				Math.max(0, lines.size() - visible)));
	}

	// --- Punch -------------------------------------------------------------

	private void punch() {
		// Nothing to punch if the buffer is a single empty line.
		if (lines.size() == 1 && lines.get(0).length() == 0) {
			statusMessage = "Nothing to punch.";
			return;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) sb.append('\n');
			sb.append(lines.get(i));
		}

		ModMessages.sendToServer(new ServerboundPunchDeckPacket(keypunchPos, sb.toString()));
		this.onClose();
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
