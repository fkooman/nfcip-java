/*
 * NFCIPConnection - NFCIP Java ME Library
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

package ds.nfcip.me;

import java.io.IOException;
import ds.nfcip.NFCIPAbstract;
import ds.nfcip.NFCIPInterface;
import ds.nfcip.NFCIPException;
import ds.nfcip.NFCIPUtils;

/**
 * Java ME implementation of NFCIPConnection
 * 
 * @author F. Kooman <F.Kooman@student.science.ru.nl>
 * 
 */
public class NFCIPConnection extends NFCIPAbstract implements NFCIPInterface {
	private com.nokia.nfc.p2p.NFCIPConnection c;
	private static final String INITIATOR_URL = "nfc:rf;type=nfcip;mode=initiator";
	private static final String TARGET_URL = "nfc:rf;type=nfcip;mode=target";

	/**
	 * Instantiate a new NFCIPConnection object
	 */
	public NFCIPConnection() {
		super();
		blockSize = 240;
	}

	/**
	 * Close current connection (release target when in INITIATOR mode)
	 * 
	 * @throws NFCIPException
	 *             if the operation fails
	 */
	public void close() throws NFCIPException {
		NFCIPUtils.debugMessage(ps, debugLevel, 2, "Closing connection...");
		if (c != null)
			try {
				c.close();
			} catch (IOException e) {
			}
	}

	protected void releaseTargets() throws NFCIPException {
		NFCIPUtils.debugMessage(ps, debugLevel, 2, "Releasing target...");
		close();
	}

	/**
	 * Set mode INITIATOR
	 * 
	 * @throws NFCIPException
	 *             if the operation fails
	 */
	protected void setInitiatorMode() {
		NFCIPUtils.debugMessage(ps, debugLevel, 2, "Setting initiator mode...");
		this.transmissionMode = SEND;
		try {
			c = (com.nokia.nfc.p2p.NFCIPConnection) javax.microedition.io.Connector
					.open(INITIATOR_URL, -1, true);
		} catch (Exception e) {
			setInitiatorMode();
		}
	}

	/**
	 * Set mode TARGET
	 * 
	 * @throws NFCIPException
	 *             if the operation fails
	 */
	protected void setTargetMode() {
		NFCIPUtils.debugMessage(ps, debugLevel, 2, "Setting target mode...");
		this.transmissionMode = RECEIVE;
		try {
			c = (com.nokia.nfc.p2p.NFCIPConnection) javax.microedition.io.Connector
					.open(TARGET_URL, -1, true);
		} catch (Exception e) {
			setTargetMode();
		}
	}

	protected void sendCommand(byte[] data) throws NFCIPException {
		NFCIPUtils.debugMessage(ps, debugLevel, 4, "Sent     ("
				+ (data != null ? data.length : 0) + " bytes): "
				+ NFCIPUtils.byteArrayToString(data));
		try {
			c.send(data);
		} catch (Exception e) {
			throw new NFCIPException("native send error: " + e.toString());
		}
	}

	protected byte[] receiveCommand() throws NFCIPException {
		try {
			byte[] recv = c.receive();
			NFCIPUtils.debugMessage(ps, debugLevel, 4, "Received ("
					+ (recv != null ? recv.length : 0) + " bytes): "
					+ NFCIPUtils.byteArrayToString(recv));
			return recv;
		} catch (Exception e) {
			throw new NFCIPException("native receive error: " + e.toString());
		}
	}
}