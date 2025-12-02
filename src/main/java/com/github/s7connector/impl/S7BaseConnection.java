/*
Copyright 2016 S7connector members (github.com/s7connector)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.github.s7connector.impl;

import com.github.s7connector.api.DaveArea;
import com.github.s7connector.api.S7Connector;
import com.github.s7connector.impl.nodave.Nodave;
import com.github.s7connector.impl.nodave.S7Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base-Connection for the S7-PLC Connection Libnodave:
 * http://libnodave.sourceforge.net/
 *
 * @author Thomas Rudin
 */
public abstract class S7BaseConnection implements S7Connector {

    private static final Logger logger = LoggerFactory.getLogger(S7BaseConnection.class);

    /**
     * The Constant MAX_SIZE.
     */
    private static final int MAX_SIZE = 96;

    /**
     * The Constant PROPERTY_AREA.
     */
    public static final String PROPERTY_AREA = "area";

    /**
     * The Constant PROPERTY_AREANUMBER.
     */
    public static final String PROPERTY_AREANUMBER = "areanumber";

    /**
     * The Constant PROPERTY_BYTES.
     */
    public static final String PROPERTY_BYTES = "bytes";

    /**
     * The Constant PROPERTY_OFFSET.
     */
    public static final String PROPERTY_OFFSET = "offset";

    /**
     * Checks the Result.
     *
     * @param libnodaveResult the libnodave result
     */
    public static void checkResult(final int libnodaveResult) {
        if (libnodaveResult != Nodave.RESULT_OK) {
            final String msg = Nodave.strerror(libnodaveResult);
            logger.error("PLC operation failed with result code {}: {}", libnodaveResult, msg);
            throw new IllegalArgumentException("Result: " + msg);
        }
    }

    /**
     * Dump data
     *
     * @param b the byte stream
     */
    protected static void dump(final byte[] b) {
        for (final byte element : b) {
            System.out.print(Integer.toHexString(element & 0xFF) + ",");
        }
    }

    /**
     * The dc.
     */
    private S7Connection dc;

    /**
     * Lock for thread-safe operations
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Initialize the connection
     *
     * @param dc the connection instance
     */
    protected void init(final S7Connection dc) {
        this.dc = dc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] read(final DaveArea area, final int areaNumber, final int bytes, final int offset) throws IOException, InterruptedException {
        if (logger.isDebugEnabled()) {
            logger.debug("Read request: area={}, areaNumber={}, bytes={}, offset={}", area, areaNumber, bytes, offset);
        }

        // Always acquire lock for the entire operation to ensure atomicity
        this.lock.lock();
        try {
            byte[] result = readInternal(area, areaNumber, bytes, offset);

            if (logger.isDebugEnabled()) {
                logger.debug("Read completed: area={}, areaNumber={}, bytes={}, offset={}, actualBytes={}",
                    area, areaNumber, bytes, offset, result.length);
            }

            return result;
        } catch (IOException e) {
            logger.error("IOException during read: area={}, areaNumber={}, bytes={}, offset={}, error={}",
                area, areaNumber, bytes, offset, e.getMessage(), e);
            throw e;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Internal read implementation without locking (lock must be held by caller).
     * Handles recursive splitting for large reads.
     */
    private byte[] readInternal(final DaveArea area, final int areaNumber, final int bytes, final int offset) throws IOException {
        if (bytes > MAX_SIZE) {
            // Handle large reads by splitting into chunks
            // Note: Lock is already held by public read() method
            if (logger.isTraceEnabled()) {
                logger.trace("Splitting read into chunks: bytes={}, MAX_SIZE={}", bytes, MAX_SIZE);
            }

            final byte[] ret = new byte[bytes];

            final byte[] currentBuffer = readInternal(area, areaNumber, MAX_SIZE, offset);
            System.arraycopy(currentBuffer, 0, ret, 0, currentBuffer.length);

            final byte[] nextBuffer = readInternal(area, areaNumber, bytes - MAX_SIZE, offset + MAX_SIZE);
            System.arraycopy(nextBuffer, 0, ret, currentBuffer.length, nextBuffer.length);

            return ret;

        } else {
            // Single read operation - lock already held
            final byte[] buffer = new byte[bytes];
            final int ret = this.dc.readBytes(area, areaNumber, offset, bytes, buffer);

            checkResult(ret);
            return buffer;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final DaveArea area, final int areaNumber, final int offset, final byte[] buffer) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Write request: area={}, areaNumber={}, offset={}, bytes={}", area, areaNumber, offset, buffer.length);
        }

        // Always acquire lock for the entire operation to ensure atomicity
        this.lock.lock();
        try {
            writeInternal(area, areaNumber, offset, buffer);

            if (logger.isDebugEnabled()) {
                logger.debug("Write completed: area={}, areaNumber={}, offset={}, bytes={}",
                    area, areaNumber, offset, buffer.length);
            }
        } catch (IOException e) {
            logger.error("IOException during write: area={}, areaNumber={}, offset={}, bytes={}, error={}",
                area, areaNumber, offset, buffer.length, e.getMessage(), e);
            throw e;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Internal write implementation without locking (lock must be held by caller).
     * Handles recursive splitting for large writes.
     */
    private void writeInternal(final DaveArea area, final int areaNumber, final int offset, final byte[] buffer) throws IOException {
        if (buffer.length > MAX_SIZE) {
            // Handle large writes by splitting into chunks
            // Note: Lock is already held by public write() method
            if (logger.isTraceEnabled()) {
                logger.trace("Splitting write into chunks: bytes={}, MAX_SIZE={}", buffer.length, MAX_SIZE);
            }

            final byte[] subBuffer = new byte[MAX_SIZE];
            final byte[] nextBuffer = new byte[buffer.length - subBuffer.length];

            System.arraycopy(buffer, 0, subBuffer, 0, subBuffer.length);
            System.arraycopy(buffer, MAX_SIZE, nextBuffer, 0, nextBuffer.length);

            writeInternal(area, areaNumber, offset, subBuffer);
            writeInternal(area, areaNumber, offset + subBuffer.length, nextBuffer);

        } else {
            // Single write operation - lock already held
            final int ret = this.dc.writeBytes(area, areaNumber, offset, buffer.length, buffer);
            // Check return-value
            checkResult(ret);
        }
    }
}
