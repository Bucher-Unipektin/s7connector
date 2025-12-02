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
import com.github.s7connector.api.SiemensPLCS;
import com.github.s7connector.exception.S7Exception;
import com.github.s7connector.impl.nodave.Nodave;
import com.github.s7connector.impl.nodave.PLCinterface;
import com.github.s7connector.impl.nodave.TCPConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * TCP_Connection to a S7 PLC
 *
 * @author Thomas Rudin
 * @href http://libnodave.sourceforge.net/
 */
public final class S7TCPConnection extends S7BaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(S7TCPConnection.class);

    /**
     * The Connection
     */
    private TCPConnection dc;

    /**
     * The Interface
     */
    private PLCinterface di;

    /**
     * The Host to connect to
     */
    private final String host;

    /**
     * The port to connect to
     */
    private final int port;

    /**
     * Connection type:
     * 1 = PG
     * 2 = OP
     * 3 = S7 Basic
     * 4-10 = Generic
     */
    private final int type;

    /**
     * Rack and slot number
     */
    private final int rack, slot;

    /**
     * Timeout number
     */
    private final int timeout;

    /**
     * The Socket
     */
    private Socket socket;

    /**
     * The connect device type,such as S200
     */
    private SiemensPLCS plcType;

    /**
     * Creates a new Instance to the given host, rack, slot and port
     *
     * @param host
     * @throws S7Exception
     */
    public S7TCPConnection(final String host, final int type, final int rack, final int slot, final int port, final int timeout, final SiemensPLCS plcType) throws S7Exception {
        logger.info("Creating S7TCP connection to {}:{} (type={}, rack={}, slot={}, timeout={}ms, plcType={})",
            host, port, type, rack, slot, timeout, plcType);

        this.host = host;
        this.type = type;
        this.rack = rack;
        this.slot = slot;
        this.port = port;
        this.timeout = timeout;
        this.plcType = plcType;

        try {
            this.setupSocket();
            logger.info("Successfully established connection to {}:{}", host, port);
        } catch (S7Exception e) {
            logger.error("Failed to create S7TCP connection to {}:{}: {}", host, port, e.getMessage(), e);
            // Mark as closed since connection failed
            markAsClosed();
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        // Check if already closed to prevent double-close issues
        if (isClosed()) {
            logger.debug("Connection to {}:{} already closed, skipping", host, port);
            return;
        }

        logger.info("Closing S7TCP connection to {}:{}", host, port);

        // Mark as closed first to prevent new operations
        markAsClosed();

        try {
            if (this.socket != null && !this.socket.isClosed()) {
                this.socket.close();
                logger.info("Successfully closed connection to {}:{}", host, port);
            } else {
                logger.debug("Socket already closed or null for {}:{}", host, port);
            }
        } catch (IOException e) {
            logger.error("Error closing connection to {}:{}: {}", host, port, e.getMessage(), e);
            throw e;
        }
    }


    /**
     * Sets up the socket
     */
    private void setupSocket() throws S7Exception {
        try {
            logger.debug("Creating socket for {}:{} with timeout {}ms", host, port, timeout);
            this.socket = new Socket();
            // Set timeout for connection establishment
            this.socket.setSoTimeout(this.timeout);
            // Enable TCP keep-alive to detect broken connections
            this.socket.setKeepAlive(true);
            // Disable Nagle's algorithm for low-latency communication
            this.socket.setTcpNoDelay(true);

            logger.debug("Connecting to {}:{}...", host, port);
            this.socket.connect(new InetSocketAddress(this.host, this.port), this.timeout);
            logger.debug("Socket connected successfully to {}:{}", host, port);

            //select the plc interface protocol by the plcsType
            int protocol;
            switch (this.plcType) {
                case S200:
                    protocol = Nodave.PROTOCOL_ISOTCP243;
                    logger.debug("Using protocol ISOTCP243 for S200");
                    break;
                case SNon200:
                case S300:
                case S400:
                case S1200:
                case S1500:
                case S200Smart:
                default:
                    protocol = Nodave.PROTOCOL_ISOTCP;
                    logger.debug("Using protocol ISOTCP for {}", plcType);
                    break;
            }

            logger.debug("Creating PLC interface with protocol {}", protocol);
            this.di = new PLCinterface(this.socket.getOutputStream(), this.socket.getInputStream(), "IF1",
                    DaveArea.LOCAL.getCode(),
                    protocol);

            logger.debug("Creating TCP connection to rack {} slot {}", rack, slot);
            this.dc = new TCPConnection(this.di, this.type, this.rack, this.slot);

            logger.debug("Connecting to PLC...");
            final int res = this.dc.connectPLC();
            checkResult(res);
            logger.debug("PLC connection established successfully");

            super.init(this.dc);

        } catch (SocketTimeoutException e) {
            // Close socket to prevent resource leak
            closeSocketSafely();
            String msg = String.format("Connection timeout while connecting to %s:%d after %dms", host, port, timeout);
            logger.error(msg, e);
            throw new S7Exception(msg, e);
        } catch (IOException e) {
            // Close socket to prevent resource leak
            closeSocketSafely();
            String msg = String.format("IOException while setting up connection to %s:%d: %s", host, port, e.getMessage());
            logger.error(msg, e);
            throw new S7Exception(msg, e);
        } catch (IllegalArgumentException e) {
            // Close socket to prevent resource leak
            closeSocketSafely();
            String msg = String.format("PLC connection check failed for %s:%d: %s", host, port, e.getMessage());
            logger.error(msg, e);
            throw new S7Exception(msg, e);
        } catch (final Exception e) {
            // Close socket to prevent resource leak
            closeSocketSafely();
            String msg = String.format("Unexpected error during connection setup to %s:%d: %s", host, port, e.getMessage());
            logger.error(msg, e);
            throw new S7Exception(msg, e);
        }
    }

    /**
     * Safely closes the socket, suppressing any exceptions.
     * Used internally for cleanup in error scenarios to prevent resource leaks.
     */
    private void closeSocketSafely() {
        if (this.socket != null && !this.socket.isClosed()) {
            try {
                this.socket.close();
                logger.debug("Socket closed during error cleanup for {}:{}", host, port);
            } catch (IOException e) {
                logger.warn("Failed to close socket during error cleanup for {}:{}: {}",
                    host, port, e.getMessage());
                // Suppress exception - we're already handling an error
            }
        }
    }
}
