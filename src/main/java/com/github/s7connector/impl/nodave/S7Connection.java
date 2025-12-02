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

import com.github.s7connector.api.DaveArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class comprises the variables and methods common to connections to an S7
 * PLC regardless of the type of transport.
 *
 * @author Thomas Hergenhahn
 */
public abstract class S7Connection {
    private static final Logger logger = LoggerFactory.getLogger(S7Connection.class);
    int answLen; // length of last message
    /**
     * position in result data, incremented when variables are extracted without
     * position
     */
    int dataPointer;
    PLCinterface iface; // pointer to used interface
    public int maxPDUlength;
    public byte[] msgIn;
    public byte[] msgOut;

    public int PDUstartIn;
    public int PDUstartOut;

    /**
     * absolute begin of result data
     */
    int udata;

    private final ReentrantLock lock = new ReentrantLock();

    public S7Connection(final PLCinterface ifa) {
        this.iface = ifa;
        this.msgIn = new byte[Nodave.MAX_RAW_LEN];
        this.msgOut = new byte[Nodave.MAX_RAW_LEN];
        this.PDUstartIn = 0;
        this.PDUstartOut = 0;
    }

    abstract public int exchange(PDU p1) throws IOException;

    /*
     * build the PDU for a PDU length negotiation
     */
    public int negPDUlengthRequest() throws IOException {
        logger.debug("Negotiating PDU length");

        this.lock.lock();

        try {
            int res;
            final PDU p = new PDU(this.msgOut, this.PDUstartOut);
            final byte[] pa = {(byte) 0xF0, 0, 0x00, 0x01, 0x00, 0x01, 0x03, (byte) 0xC0};
            p.initHeader(1);
            p.addParam(pa);
            res = this.exchange(p);
            if (res != 0) {
                logger.warn("PDU length negotiation exchange failed: result={}, error={}", res, Nodave.strerror(res));
                return res;
            }
            final PDU p2 = new PDU(this.msgIn, this.PDUstartIn);
            res = p2.setupReceivedPDU();
            if (res != 0) {
                logger.warn("Failed to setup PDU length negotiation response: result={}, error={}", res, Nodave.strerror(res));
                return res;
            }
            this.maxPDUlength = Nodave.USBEWord(this.msgIn, p2.param + 6);
            logger.info("Negotiated PDU length: {}", this.maxPDUlength);
            return res;

        } catch (IOException e) {
            logger.error("IOException during PDU length negotiation: {}", e.getMessage(), e);
            throw e;
        } finally {
            this.lock.unlock();
        }
    }

    public int readBytes(final DaveArea area, final int DBnum, final int start, final int len, final byte[] buffer) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Reading {} bytes from area {} DB {} at offset {}", len, area, DBnum, start);
        }

        this.lock.lock();

        try {
            final PDU p1 = new PDU(this.msgOut, this.PDUstartOut);
            p1.initReadRequest();
            p1.addVarToReadRequest(area, DBnum, start, len);

            int res = this.exchange(p1);
            if (res != Nodave.RESULT_OK) {
                logger.warn("Exchange failed during read: result={}, error={}", res, Nodave.strerror(res));
                return res;
            }
            final PDU p2 = new PDU(this.msgIn, this.PDUstartIn);
            res = p2.setupReceivedPDU();
            if (res != Nodave.RESULT_OK) {
                logger.warn("Failed to setup received PDU: result={}, error={}", res, Nodave.strerror(res));
                return res;
            }

            res = p2.testReadResult();
            if (res != Nodave.RESULT_OK) {
                logger.warn("Read result test failed: result={}, error={}", res, Nodave.strerror(res));
                return res;
            }
            if (p2.udlen == 0) {
                logger.warn("CPU returned no data for area {} DB {} at offset {}", area, DBnum, start);
                return Nodave.RESULT_CPU_RETURNED_NO_DATA;
            }
            /*
             * copy to user buffer and setup internal buffer pointers:
             */
            if (buffer != null) {
                System.arraycopy(p2.mem, p2.udata, buffer, 0, p2.udlen);
            }

            this.dataPointer = p2.udata;
            this.udata = p2.udata;
            this.answLen = p2.udlen;

            if (logger.isDebugEnabled()) {
                logger.debug("Successfully read {} bytes from area {} DB {} at offset {}", p2.udlen, area, DBnum, start);
            }

            return res;

        } catch (IOException e) {
            logger.error("IOException during read from area {} DB {} at offset {}: {}", area, DBnum, start, e.getMessage(), e);
            throw e;
        } finally {
            this.lock.unlock();
        }
    }

    /*
     * Write len bytes to PLC memory area "area", data block DBnum.
     */
    public int writeBytes(final DaveArea area, final int DBnum, final int start, final int len, final byte[] buffer) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Writing {} bytes to area {} DB {} at offset {}", len, area, DBnum, start);
        }

        this.lock.lock();
        int errorState;

        try {
            final PDU p1 = new PDU(this.msgOut, this.PDUstartOut);

            // p1.constructWriteRequest(area, DBnum, start, len, buffer);
            p1.prepareWriteRequest();
            p1.addVarToWriteRequest(area, DBnum, start, len, buffer);

            errorState = this.exchange(p1);

            if (errorState == 0) {
                final PDU p2 = new PDU(this.msgIn, this.PDUstartIn);
                p2.setupReceivedPDU();

                if (p2.mem[p2.param] == PDU.FUNC_WRITE) {
                    if (p2.mem[p2.data] == (byte) 0xFF) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Successfully wrote {} bytes to area {} DB {} at offset {}", len, area, DBnum, start);
                        }
                        return 0;
                    }
                } else {
                    logger.warn("Unexpected response during write to area {} DB {}: param function mismatch", area, DBnum);
                    errorState |= 4096;
                }
            } else {
                logger.warn("Exchange failed during write: errorState={}, error={}", errorState, Nodave.strerror(errorState));
            }
        } catch (IOException e) {
            logger.error("IOException during write to area {} DB {} at offset {}: {}", area, DBnum, start, e.getMessage(), e);
            throw e;
        } finally {
            this.lock.unlock();
        }

        return errorState;
    }
}
