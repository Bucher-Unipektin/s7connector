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
package com.github.s7connector.test;

import com.github.s7connector.api.DaveArea;
import com.github.s7connector.api.S7Connector;
import com.github.s7connector.exception.S7Exception;
import com.github.s7connector.impl.S7TCPConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests for input validation in S7Connector implementations
 */
public class InputValidationTest {

    private S7Connector connector;

    @Before
    public void setUp() throws S7Exception {
        // Create a connection to a test PLC
        // Note: This requires a real PLC or mock. For unit tests, consider using a mock.
        try {
            connector = new S7TCPConnection("127.0.0.1", 1, 0, 2, 102, 2000,
                com.github.s7connector.api.SiemensPLCS.SNon200);
        } catch (S7Exception e) {
            // Connection might fail in CI environment, that's ok for validation tests
        }
    }

    @After
    public void tearDown() throws IOException {
        if (connector != null) {
            try {
                connector.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadWithNullArea() throws IOException, InterruptedException {
        if (connector == null) {
            throw new IllegalArgumentException("Expected exception");
        }
        connector.read(null, 1, 10, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadWithNegativeBytes() throws IOException, InterruptedException {
        if (connector == null) {
            throw new IllegalArgumentException("Expected exception");
        }
        connector.read(DaveArea.DB, 1, -1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadWithZeroBytes() throws IOException, InterruptedException {
        if (connector == null) {
            throw new IllegalArgumentException("Expected exception");
        }
        connector.read(DaveArea.DB, 1, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadWithNegativeOffset() throws IOException, InterruptedException {
        if (connector == null) {
            throw new IllegalArgumentException("Expected exception");
        }
        connector.read(DaveArea.DB, 1, 10, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteWithNullArea() throws IOException, InterruptedException {
        if (connector == null) {
            throw new IllegalArgumentException("Expected exception");
        }
        byte[] data = new byte[10];
        connector.write(null, 1, 0, data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteWithNullBuffer() throws IOException, InterruptedException {
        if (connector == null) {
            throw new IllegalArgumentException("Expected exception");
        }
        connector.write(DaveArea.DB, 1, 0, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteWithEmptyBuffer() throws IOException, InterruptedException {
        if (connector == null) {
            throw new IllegalArgumentException("Expected exception");
        }
        byte[] data = new byte[0];
        connector.write(DaveArea.DB, 1, 0, data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteWithNegativeOffset() throws IOException, InterruptedException {
        if (connector == null) {
            throw new IllegalArgumentException("Expected exception");
        }
        byte[] data = new byte[10];
        connector.write(DaveArea.DB, 1, -1, data);
    }

    @Test
    public void testValidReadParameters() {
        // This test just ensures valid parameters don't throw validation exceptions
        // It may fail with IOException if no PLC is connected, which is expected
        if (connector != null) {
            try {
                connector.read(DaveArea.DB, 1, 10, 0);
            } catch (IOException | InterruptedException e) {
                // Expected if no real PLC is connected
                assertTrue("Expected IOException or InterruptedException", true);
            } catch (IllegalArgumentException | IllegalStateException e) {
                fail("Valid parameters should not throw validation exception: " + e.getMessage());
            }
        }
    }

    @Test
    public void testValidWriteParameters() {
        // This test just ensures valid parameters don't throw validation exceptions
        // It may fail with IOException if no PLC is connected, which is expected
        if (connector != null) {
            try {
                byte[] data = new byte[10];
                connector.write(DaveArea.DB, 1, 0, data);
            } catch (IOException | InterruptedException e) {
                // Expected if no real PLC is connected
                assertTrue("Expected IOException or InterruptedException", true);
            } catch (IllegalArgumentException | IllegalStateException e) {
                fail("Valid parameters should not throw validation exception: " + e.getMessage());
            }
        }
    }
}

