/*
 Part of Libnodave, a free communication libray for Siemens S7
 
 (C) Thomas Hergenhahn (thomas.hergenhahn@web.de) 2005.

 Libnodave is free software; you can redistribute it and/or modify
 it under the terms of the GNU Library General Public License as published by
 the Free Software Foundation; either version 2, or (at your option)
 any later version.

 Libnodave is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU Library General Public License
 along with this; see the file COPYING.  If not, write to
 the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.  
*/
package com.github.s7connector.impl.nodave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * The Class TCPConnection.
 */
public final class TCPConnection extends S7Connection {

    private static final Logger logger = LoggerFactory.getLogger(TCPConnection.class);

    /**
     * The connection type.
     */
    int type;

    /**
     * The rack.
     */
    int rack;

    /**
     * The slot.
     */
    int slot;

    /**
     * Instantiates a new TCP connection.
     *
     * @param ifa  the plc interface
     * @param type the connection type (1=PG, 2=OP, 3-10=Generic)
     * @param rack the rack (typically 0-7)
     * @param slot the slot (typically 0-31)
     * @throws IllegalArgumentException if parameters are out of valid range
     */
    public TCPConnection(final PLCinterface ifa, final int type, final int rack, final int slot) {
        super(ifa, 7, 7);  // TCPConnection uses PDU start offsets of 7

        // Validate parameters to prevent protocol errors
        if (rack < 0 || rack > 7) {
            throw new IllegalArgumentException("Rack must be between 0 and 7, but was: " + rack);
        }
        if (slot < 0 || slot > 31) {
            throw new IllegalArgumentException("Slot must be between 0 and 31, but was: " + slot);
        }
        if (type < 1 || type > 10) {
            throw new IllegalArgumentException("Type must be between 1 and 10, but was: " + type);
        }

        this.type = type;
        this.rack = rack;
        this.slot = slot;
    }

    /**
     * We have our own connectPLC(), but no disconnect() Open connection to a
     * PLC. This assumes that dc is initialized by daveNewConnection and is not
     * yet used. (or reused for the same PLC ?)
     *
     * @return the int
     */
    public int connectPLC() throws IOException {
        logger.debug("Connecting to PLC: rack={}, slot={}", rack, slot);

        int packetLength;
        if (iface.protocol == Nodave.PROTOCOL_ISOTCP243) {
        	final byte[] b243 = {
        			(byte) 0x11, (byte) 0xE0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
        			(byte) 0xC1, (byte) 0x02, (byte) 0x4D, (byte) 0x57, (byte) 0xC2, (byte) 0x02, (byte) 0x4D, (byte) 0x57,
        			(byte) 0xC0, (byte) 0x01, (byte) 0x09
        	};
            System.arraycopy(b243, 0, this.msgOut, 4, b243.length);
            packetLength = b243.length;
        } else {
        	final byte[] b4 = {
        			(byte) 0x11, (byte) 0xE0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
        			(byte) 0xC1, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0xC2, (byte) 0x02, (byte) 0x01, (byte) 0x02,
        			(byte) 0xC0, (byte) 0x01, (byte) 0x09
        	};
        	System.arraycopy(b4, 0, this.msgOut, 4, b4.length);
            this.msgOut[17] = (byte) this.type;
            this.msgOut[18] = (byte) ((16 * (this.rack *2) ) +this.slot);
            packetLength = b4.length;
        }
        this.sendISOPacket(packetLength);
        this.readISOPacket();
        /*
         * PDU p = new PDU(msgOut, 7); p.initHeader(1); p.addParam(b61);
         * exchange(p); return (0);
         */
        int result = this.negPDUlengthRequest();

        if (result == 0) {
            logger.info("Successfully connected to PLC: rack={}, slot={}", rack, slot);
        } else {
            logger.warn("PLC connection completed with non-zero result: {}", result);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int exchange(final PDU p1) throws IOException {
        if (logger.isTraceEnabled()) {
            logger.trace("Exchanging PDU: hlen={}, plen={}, dlen={}", p1.hlen, p1.plen, p1.dlen);
        }

        this.msgOut[4] = (byte) 0x02;
        this.msgOut[5] = (byte) 0xf0;
        this.msgOut[6] = (byte) 0x80;
        this.sendISOPacket(3 + p1.hlen + p1.plen + p1.dlen);
        this.readISOPacket();

        if (logger.isTraceEnabled()) {
            logger.trace("PDU exchange completed successfully");
        }

        return 0;
    }

    /**
     * Read iso packet.
     *
     * @return the int
     * @throws IOException if an I/O error occurs while reading
     */
    protected int readISOPacket() throws IOException {
        int res = this.iface.read(this.msgIn, 0, 4);
        if (res == 4) {
            // Java bytes are signed - must mask with 0xFF to get unsigned value
            // Without masking: msgIn[2] = 0xFF (byte -1) → -256 instead of 255
            final int len = ((this.msgIn[2] & 0xFF) * 0x100) + (this.msgIn[3] & 0xFF);

            // Validate length to prevent buffer overflow
            if (len < 0 || len > this.msgIn.length - 4) {
                logger.error("Invalid packet length received: {} (max allowed: {})", len, this.msgIn.length - 4);
                throw new IOException("Invalid ISO packet length: " + len);
            }

            res += this.iface.read(this.msgIn, 4, len);
        } else if (res > 0) {
            // Incomplete header read - this indicates a protocol issue or connection problem
            logger.warn("Incomplete ISO packet header: expected 4 bytes, got {} bytes. Connection may be unstable.", res);
            return 0;
        } else {
            // No data available (res == 0) or error
            return 0;
        }
        return res;
    }

    /**
     * Send iso packet.
     *
     * @param size the size
     * @return the int
     */
    protected int sendISOPacket(int size) throws IOException {
        size += 4;

        // Validate size to prevent buffer overflow
        if (size < 4 || size > this.msgOut.length) {
            logger.error("Invalid ISO packet size: {} (must be between 4 and {})", size, this.msgOut.length);
            throw new IOException("Invalid ISO packet size: " + size);
        }

        this.msgOut[0] = (byte) 0x03;
        this.msgOut[1] = (byte) 0x0;
        this.msgOut[2] = (byte) (size / 0x100);
        this.msgOut[3] = (byte) (size % 0x100);
        /*
         * if (messageNumber == 0) { messageNumber = 1; msgOut[11] = (byte)
         * ((messageNumber + 1) & 0xff); messageNumber++; messageNumber &= 0xff;
         * //!! }
         */

        this.iface.write(this.msgOut, 0, size);
        return 0;
    }
}
