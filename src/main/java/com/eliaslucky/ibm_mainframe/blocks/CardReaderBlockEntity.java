package com.eliaslucky.ibm_mainframe.blocks;

import com.eliaslucky.ibm_mainframe.channel.*;
import com.eliaslucky.ibm_mainframe.items.CardDeckItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The IBM 2540 Card Read Punch. Reads a deck one card at a time,
 * 80 columns per card.
 */
public class CardReaderBlockEntity extends BlockEntity implements ChannelDevice {
	private ItemStack deck = ItemStack.EMPTY;
	private int headIndex = 0;	 // next card to read

	// TODO: Currently it's read-only from program's perspective. Later make it so it can write back to a punched desk using command WRITE
	public CardReaderBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CARD_READER.get(), pos, state);
	}

	// --- Deck manipulation -------------------------------------------------

	public boolean insertDeck(ItemStack stack) {
		if (!deck.isEmpty()) return false;
		if (!(stack.getItem() instanceof CardDeckItem)) return false;
		this.deck = stack.copyWithCount(1);
		this.headIndex = 0;
		setChanged();
		return true;
	}

	public boolean hasDeck() { return !deck.isEmpty(); }
	public int deckSize()  { return deck.isEmpty() ? 0 : CardDeckItem.getCards(deck).size(); }

	public ItemStack ejectDeck() {
		ItemStack out = deck;
		deck = ItemStack.EMPTY;
		headIndex = 0;
		setChanged();
		return out;
	}

	public String statusLine() {
		if (deck.isEmpty()) return "2540: no deck loaded.";
		return "2540: " + deckSize() + " cards, head at card " + headIndex + ".";
	}

	// --- ChannelDevice -----------------------------------------------------

	@Override public String deviceName() { return "CARD-R"; }

	@Override
	public ChannelResult execute(ChannelCommand cmd) {
		if (deck.isEmpty()) return ChannelResult.NOT_READY;

		return switch (cmd.op()) {
			case READ -> readOneCard();
			case SENSE -> ChannelResult.read(senseBytes());
			case CONTROL -> handleControl(cmd);
			case WRITE -> ChannelResult.REJECT;   // punch not implemented in v1
			case NOP -> ChannelResult.OK;
		};
	}

	private ChannelResult readOneCard() {
		List<String> cards = CardDeckItem.getCards(deck);
		if (headIndex >= cards.size()) {
			return ChannelResult.UNIT_EXCEPTION;   // end of deck
		}
		String line = cards.get(headIndex++);
		setChanged();
		byte[] bytes = line.getBytes(StandardCharsets.US_ASCII);
		// Truncate or pad to exactly 80 columns.
		byte[] image = new byte[80];
		for (int i = 0; i < 80; i++) image[i] = (i < bytes.length) ? bytes[i] : (byte) ' ';
		return ChannelResult.read(image);
	}

	private byte[] senseBytes() {
		// One byte: bit 0 set = end of file; bit 1 set = deck not loaded.
		int s = 0;
		List<String> cards = CardDeckItem.getCards(deck);
		if (headIndex >= cards.size()) s |= 0x01;
		return new byte[] { (byte) s };
	}

	private ChannelResult handleControl(ChannelCommand cmd) {
		byte[] p = cmd.payload();
		if (p == null || p.length == 0) return ChannelResult.REJECT;
		return switch (p[0]) {
			case ChannelControl.REWIND -> {
				headIndex = 0; setChanged(); yield ChannelResult.OK;
			}
			case ChannelControl.FEED -> {
				List<String> cards = CardDeckItem.getCards(deck);
				if (headIndex < cards.size()) headIndex++;
				setChanged();
				yield ChannelResult.OK;
			}
			case ChannelControl.EJECT -> {
				// Deferred ejection: the deck stays loaded, but the head
				// moves to the end so subsequent reads return EOF.
				headIndex = deckSize();
				setChanged();
				yield ChannelResult.OK;
			}
			default -> ChannelResult.REJECT;
		};
	}

	// --- Peripheral (legacy byte-stream API) ------------------------------
	// No-op: the CCW path is the real interface. These exist so the
	// device can be surfaced via DeviceHandler.of(this) if a PC kernel
	// ever sees it on its bus.

	@Override public String deviceClass() { return "card_reader"; }
	@Override public String vendorId()	  { return "ibm_mainframe"; }
	@Override public String productId()   { return "ibm_2540"; }
	@Override public String description() { return "IBM 2540 Card Read Punch"; }
	@Override public void write(byte[] d) {}
	@Override public byte[] read(int maxBytes) { return new byte[0]; }
	@Override public int ioctl(int cmd, byte[] arg) { return -1; }
	@Override public boolean isReady() { return !deck.isEmpty(); }
	@Override public boolean hasData() { return false; }

	// --- NBT --------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (!deck.isEmpty()) tag.put("Deck", deck.save(new CompoundTag()));
		tag.putInt("HeadIndex", headIndex);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		this.deck = tag.contains("Deck")
				? ItemStack.of(tag.getCompound("Deck"))
				: ItemStack.EMPTY;
		this.headIndex = tag.getInt("HeadIndex");
	}
}
