package com.eliaslucky.ibm_mainframe.channel;

/** Standard device control sub-opcodes carried in a CONTROL CCW's payload. */
public final class ChannelControl {
	private ChannelControl() {}
	// CARD READER
	public static final byte REWIND		 = 0x01;  // tape, card reader
	public static final byte FEED		 = 0x02;  // skip one card
	public static final byte EJECT		 = 0x03;  // unload deck
	
	// PRINTER
	public static final byte FORM_FEED	 = 0x04;  // page break
	public static final byte CARRIAGE_RETURN = 0x05;  // new line
	public static final byte NEW_PAGE	 = 0x06;  // clear page counter

	// TAPE
	public static final byte FORWARD_SPACE   = 0x10; // skip one record
	public static final byte BACKSPACE       = 0x11; // back up one record
	public static final byte FORWARD_FILE    = 0x12; // skip to next tape mark
	public static final byte WEOF            = 0x13; // write tape mark
}
