/*
 * NFCIPConnection - Java SE implementation of NFCIPConnection for the ACS 
 *                   ACR122
 * 
 * Copyright (C) 2009  François Kooman <F.Kooman@student.science.ru.nl>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package ds.nfcip.se;

import java.util.List;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

import ds.nfcip.NFCIPAbstract;
import ds.nfcip.NFCIPException;
import ds.nfcip.NFCIPInterface;
import ds.nfcip.NFCIPUtils;

/**
 * Java SE implementation of NFCIPConnection for the ACS ACR122
 * 
 * @author F. Kooman <F.Kooman@student.science.ru.nl>
 * 
 */
public class NFCIPConnection extends NFCIPAbstract implements NFCIPInterface {

	/* PN53x instructions */
	// private final byte GET_GENERAL_STATUS = (byte) 0x04;
	private final byte IN_DATA_EXCHANGE = (byte) 0x40;
	// private final byte IN_LIST_PASSIVE_TARGET = (byte) 0x4a;
	// private final byte IN_ATR = (byte) 0x50;
	private final byte IN_RELEASE = (byte) 0x52;
	private final byte IN_JUMP_FOR_DEP = (byte) 0x56;
	private final byte TG_GET_DATA = (byte) 0x86;
	private final byte TG_INIT_AS_TARGET = (byte) 0x8c;
	private final byte TG_SET_DATA = (byte) 0x8e;
	// private final byte TG_SET_META_DATA = (byte) 0x94;
	private final byte[] GET_FIRMWARE_VERSION = { (byte) 0xff, (byte) 0x00,
			(byte) 0x48, (byte) 0x00, (byte) 0x00 };

	/**
	 * temporary buffer for storing data from sendCommand when in initiator mode
	 */
	private byte[] tmpSendStorage;

	private CardTerminal terminal = null;
	private CardChannel ch = null;

	/**
	 * Instantiate a new NFCIPConnection object
	 */
	public NFCIPConnection() {
		super();
		blockSize = 240;
	}

	private void connectToTerminal() throws NFCIPException {
		if (terminal == null)
			throw new NFCIPException("need to set terminal device first");
		Card card;
		try {
			if (terminal.isCardPresent()) {
				card = terminal.connect("*");
				ch = card.getBasicChannel();
			} else {
				throw new NFCIPException("unsupported device");
			}
		} catch (CardException e) {
			throw new NFCIPException("problem with connecting to reader");
		}
		NFCIPUtils.debugMessage(ps, debugLevel, 2, "successful connection");
		NFCIPUtils.debugMessage(ps, debugLevel, 2,
				"ACS ACR122 firmware version: " + getFirmwareVersion());
	}

	/**
	 * Close current connection
	 * 
	 * @throws NFCIPException
	 *             if the operation fails
	 */
	public void close() throws NFCIPException {
		if (mode == FAKE_INITIATOR)
			sendCommand(new byte[] { (byte) 0x89 });
	}

	protected void releaseTargets() throws NFCIPException {
		if (mode == INITIATOR || mode == FAKE_TARGET) {
			/* release all targets */
			transmit(IN_RELEASE, new byte[] { 0x00 });
			/*
			 * sleep after a release of target to turn off the radio for a while
			 * which helps with reconnecting to the phone that needs a little
			 * more time to reset target mode
			 */
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Set mode INITIATOR
	 * 
	 * @throws NFCIPException
	 *             if the operation fails
	 */
	protected void setInitiatorMode() throws NFCIPException {
		this.transmissionMode = SEND;
		// byte[] initiatorPayload = { 0x00, 0x00, 0x00 }; // passive, 106kbps
		byte[] initiatorPayload = { 0x00, 0x02, 0x01, 0x00, (byte) 0xff,
				(byte) 0xff, 0x00, 0x00 }; // passive, 424kbps
		// byte[] initiatorPayload = { 0x01, 0x00, 0x00 }; // active, 106kbps
		// byte[] initiatorPayload = { 0x01, 0x02, 0x00 }; // active, 424kbps

		transmit(IN_JUMP_FOR_DEP, initiatorPayload);
	}

	/**
	 * Set mode TARGET
	 * 
	 * @throws NFCIPException
	 *             if the operation fails
	 */
	protected void setTargetMode() throws NFCIPException {
		this.transmissionMode = RECEIVE;
		byte[] targetPayload = { (byte) 0x00, (byte) 0x08, (byte) 0x00,
				(byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x40,
				(byte) 0x01, (byte) 0xFE, (byte) 0xA2, (byte) 0xA3,
				(byte) 0xA4, (byte) 0xA5, (byte) 0xA6, (byte) 0xA7,
				(byte) 0xC0, (byte) 0xC1, (byte) 0xC2, (byte) 0xC3,
				(byte) 0xC4, (byte) 0xC5, (byte) 0xC6, (byte) 0xC7,
				(byte) 0xFF, (byte) 0xFF, (byte) 0xAA, (byte) 0x99,
				(byte) 0x88, (byte) 0x77, (byte) 0x66, (byte) 0x55,
				(byte) 0x44, (byte) 0x33, (byte) 0x22, (byte) 0x11,
				(byte) 0x00, (byte) 0x00 };
		transmit(TG_INIT_AS_TARGET, targetPayload);
	}

	/**
	 * Set the terminal to use
	 * 
	 * @param terminalNumber
	 *            the terminal to use, specify this as a number, the first
	 *            terminal has number 0
	 */
	public void setTerminal(int terminalNumber) throws NFCIPException {
		List<CardTerminal> terminals;
		try {
			TerminalFactory factory = TerminalFactory.getDefault();
			terminals = factory.terminals().list();
			if (terminals.size() == 0)
				terminals = null;
		} catch (CardException c) {
			terminals = null;
		}
		if (terminals != null && terminalNumber >= 0
				&& terminalNumber < terminals.size())
			terminal = terminals.get(terminalNumber);
		connectToTerminal();
	}

	/**
	 * Sends and receives APDUs to and from the PN53x, handles APDU and NFCIP
	 * data transfer error handling.
	 * 
	 * @param instr
	 *            The PN53x instruction
	 * @param param
	 *            The payload to send
	 * 
	 * @return The response payload (without instruction bytes and status bytes)
	 */
	private byte[] transmit(byte instr, byte[] payload) throws NFCIPException {
		if (ch == null)
			throw new NFCIPException("channel not open");

		NFCIPUtils.debugMessage(ps, debugLevel, 3, instructionToString(instr));

		int payloadLength = (payload != null) ? payload.length : 0;
		byte[] instruction = { (byte) 0xd4, instr };

		/* ACR122 header */
		byte[] header = { (byte) 0xff, (byte) 0x00, (byte) 0x00, (byte) 0x00,
				(byte) (instruction.length + payloadLength) };

		/* construct the command */
		byte[] cmd = NFCIPUtils.appendToByteArray(header, instruction, 0,
				instruction.length);
		/*
		 * if we are initiator and we want to send data to a target we need to
		 * add the target, either 0x01 or 0x02 because the PN53x supports 2
		 * targets at once. IN_JUMP_FOR_DEP handles only one target, so we use
		 * 0x01 here
		 */
		if (instr == IN_DATA_EXCHANGE) {
			cmd = NFCIPUtils.appendToByteArray(cmd, new byte[] { 0x01 }, 0, 1);
			/* increase APDU length byte */
			cmd[4]++;
		}

		cmd = NFCIPUtils.appendToByteArray(cmd, payload);

		try {
			NFCIPUtils.debugMessage(ps, debugLevel, 4, "Sent     ("
					+ cmd.length + " bytes): "
					+ NFCIPUtils.byteArrayToString(cmd));

			CommandAPDU c = new CommandAPDU(cmd);
			ResponseAPDU r = ch.transmit(c);

			byte[] ra = r.getBytes();

			NFCIPUtils.debugMessage(ps, debugLevel, 4, "Received (" + ra.length
					+ " bytes): " + NFCIPUtils.byteArrayToString(ra));

			/* check whether APDU command was accepted by the ACS ACR122 */
			if (r.getSW1() == 0x63 && r.getSW2() == 0x27) {
				throw new CardException(
						"wrong checksum from contactless response (0x63 0x27");
			} else if (r.getSW1() == 0x63 && r.getSW2() == 0x7f) {
				throw new CardException("wrong PN53x command (0x63 0x7f)");
			} else if (r.getSW1() != 0x90 && r.getSW2() != 0x00) {
				throw new CardException("unknown error ("
						+ NFCIPUtils.byteToString(r.getSW1()) + " "
						+ NFCIPUtils.byteToString(r.getSW2()));
			}

			/*
			 * some responses to commands have a status field, we check that
			 * here, this applies for TgSetData, TgGetData and InDataExchange.
			 */
			if ((instr == TG_SET_DATA || instr == TG_GET_DATA || instr == IN_DATA_EXCHANGE)
					&& ra[2] != (byte) 0x00) {
				throw new NFCIPException("communication error ("
						+ NFCIPUtils.byteToString(ra[2]) + ")");
			}
			/* strip of the response command codes and the status field */
			ra = NFCIPUtils.subByteArray(ra, 2, ra.length - 4);

			/*
			 * remove status byte from result as we don't need this for custom
			 * chaining
			 */
			if (instr == TG_GET_DATA || instr == IN_DATA_EXCHANGE) {
				ra = NFCIPUtils.subByteArray(ra, 1, ra.length - 1);
			}
			return ra;
		} catch (CardException e) {
			throw new NFCIPException("problem with transmitting data ("
					+ e.getMessage() + ")");
		}
	}

	/**
	 * Convert an instruction byte to a human readable text
	 * 
	 * @param instr
	 *            the instruction byte
	 * @return the human readable text
	 */
	private String instructionToString(byte instr) {
		switch (instr) {
		case IN_DATA_EXCHANGE:
			return "IN_DATA_EXCHANGE";
		case IN_RELEASE:
			return "IN_RELEASE";
		case IN_JUMP_FOR_DEP:
			return "IN_JUMP_FOR_DEP";
		case TG_GET_DATA:
			return "TG_GET_DATA";
		case TG_INIT_AS_TARGET:
			return "TG_INIT_AS_TARGET";
		case TG_SET_DATA:
			return "TG_SET_DATA";
		default:
			return "UNKNOWN INSTRUCTION (" + NFCIPUtils.byteToString(instr)
					+ ")";
		}
	}

	protected void sendCommand(byte[] data) throws NFCIPException {
		if (mode == INITIATOR || mode == FAKE_TARGET) {
			tmpSendStorage = transmit(IN_DATA_EXCHANGE, data);
		} else {
			transmit(TG_SET_DATA, data);
		}
	}

	protected byte[] receiveCommand() throws NFCIPException {
		if (mode == INITIATOR || mode == FAKE_TARGET) {
			return tmpSendStorage;
		} else {
			return transmit(TG_GET_DATA, null);
		}
	}

	private String getFirmwareVersion() throws NFCIPException {
		try {
			CommandAPDU c = new CommandAPDU(GET_FIRMWARE_VERSION);
			if (ch == null) {
				throw new NFCIPException("channel not open");
			}
			return new String(ch.transmit(c).getBytes());
		} catch (CardException e) {
			throw new NFCIPException("problem requesting firmware version");
		}
	}
}