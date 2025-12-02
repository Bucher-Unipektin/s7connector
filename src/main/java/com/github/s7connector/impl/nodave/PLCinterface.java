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
import java.io.InputStream;
import java.io.OutputStream;

public final class PLCinterface {
	private static final Logger logger = LoggerFactory.getLogger(PLCinterface.class);
    InputStream in;
    int localMPI; // the adapter's MPI address
    String name;

    OutputStream out;
    int protocol; // The kind of transport used on this interface.
    int wp, rp;

    public PLCinterface(final OutputStream out, final InputStream in, final String name, final int localMPI,
                        final int protocol) {
        this.init(out, in, name, localMPI, protocol);
    }

    public void init(final OutputStream oStream, final InputStream iStream, final String name, final int localMPI,
                     final int protocol) {
        this.out = oStream;
        this.in = iStream;
        this.name = name;
        this.localMPI = localMPI;
        this.protocol = protocol;
    }

	public int read(final byte[] b, int start, int len) {
		if (logger.isTraceEnabled()) {
			logger.trace("Reading {} bytes from PLC interface '{}'", len, name);
		}

		int res;
		try {
			int retry = 0;
			while ((this.in.available() <= 0) && (retry < 500)) {
				try {
					if (retry > 0) {
						Thread.sleep(1);
					}
					retry++;
				} catch (final InterruptedException e) {
					logger.warn("Thread interrupted while waiting for data from PLC interface '{}'. Thread will terminate.", name, e);
					// Restore interrupted status so calling code can handle it
					Thread.currentThread().interrupt();
					// Return early to allow thread to terminate gracefully
					return 0;
				}
			}

			if (this.in.available() <= 0 && retry >= 500) {
				logger.debug("Timeout waiting for data from PLC interface '{}' after {} retries", name, retry);
			}

			res = 0;
			while ((this.in.available() > 0) && (len > 0)) {
				int bytesRead = this.in.read(b, start, len);
				if (bytesRead > 0) {
					res += bytesRead;
					start += bytesRead;
					len -= bytesRead;
				} else if (bytesRead < 0) {
					logger.warn("End of stream reached on PLC interface '{}'", name);
					break;
				} else {
					break;
				}
			}

			if (logger.isTraceEnabled()) {
				logger.trace("Successfully read {} bytes from PLC interface '{}'", res, name);
			}

			return res;
		} catch (final IOException e) {
			logger.error("IOException while reading from PLC interface '{}': {}", name, e.getMessage(), e);
			return 0;
		}
	}

	public void write(final byte[] b, final int start, final int len) throws IOException {
		if (logger.isTraceEnabled()) {
			logger.trace("Writing {} bytes to PLC interface '{}'", len, name);
		}

		try {
			this.out.write(b, start, len);

			if (logger.isTraceEnabled()) {
				logger.trace("Successfully wrote {} bytes to PLC interface '{}'", len, name);
			}
		} catch (final IOException e) {
			logger.error("IOException while writing to PLC interface '{}': {}", name, e.getMessage(), e);
			throw e;
		}
	}
}
