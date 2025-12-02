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
package com.github.s7connector.api;

import java.io.Closeable;
import java.io.IOException;

public interface S7Connector extends Closeable {
	/**
	 * Reads data from a specified memory area of the PLC.
	 *
	 * @param area the memory area to read from (e.g., DB, Inputs, Outputs, Flags)
	 * @param areaNumber the area number (e.g., DB number for data blocks)
	 * @param bytes the number of bytes to read (must be positive)
	 * @param offset the byte offset within the area (must be non-negative)
	 * @return byte array containing the read data
	 * @throws IOException if an I/O error occurs during communication with the PLC
	 * @throws InterruptedException if the thread is interrupted while waiting for the lock or during I/O
	 * @throws IllegalArgumentException if parameters are invalid (negative values, null area)
	 * @throws IllegalStateException if the connection is not initialized
	 */
	byte[] read(DaveArea area, int areaNumber, int bytes, int offset) throws IOException, InterruptedException;

	/**
	 * Writes data to a specified memory area of the PLC.
	 *
	 * @param area the memory area to write to (e.g., DB, Inputs, Outputs, Flags)
	 * @param areaNumber the area number (e.g., DB number for data blocks)
	 * @param offset the byte offset within the area (must be non-negative)
	 * @param buffer the data to write (must not be null or empty)
	 * @throws IOException if an I/O error occurs during communication with the PLC
	 * @throws InterruptedException if the thread is interrupted while waiting for the lock or during I/O
	 * @throws IllegalArgumentException if parameters are invalid (negative offset, null/empty buffer, null area)
	 * @throws IllegalStateException if the connection is not initialized
	 */
	void write(DaveArea area, int areaNumber, int offset, byte[] buffer) throws IOException, InterruptedException;

}
