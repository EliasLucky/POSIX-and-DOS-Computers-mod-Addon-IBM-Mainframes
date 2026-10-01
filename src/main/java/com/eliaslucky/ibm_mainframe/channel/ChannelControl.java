package com.eliaslucky.ibm_mainframe.channel;

/** Standard device control sub-opcodes carried in a CONTROL CCW's payload. */
public final class ChannelControl {
	private ChannelControl() {}
	public static final byte REWIND			= 0x01;  // tape, card reader
	public static final byte FEED			= 0x02;  // card reader: skip one card
	public static final byte EJECT			= 0x03;  // card reader: unload deck
	public static final byte FORM_FEED		= 0x04;  // printer: page break
	public static final byte CARRIAGE_RETURN = 0x05; // printer: new line
	public static final byte NEW_PAGE		= 0x06;  // printer: clear page counter
}
