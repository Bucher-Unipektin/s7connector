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
package com.github.s7connector.test.converter;

import com.github.s7connector.impl.serializer.converter.StringConverter;
import org.junit.Assert;
import org.junit.Test;

public class StringConverterTest {

	@Test
	public void insert1() {
		StringConverter c = new StringConverter();
		byte[] buffer = new byte[10];
		c.insert("Hello", buffer, 0, 0, 8);
		byte[] expected = new byte[]{8, 5, 'H', 'e', 'l', 'l', 'o', 0, 0, 0};
		Assert.assertArrayEquals(expected, buffer);
	}

	@Test
	public void insert2() {
		StringConverter c = new StringConverter();
		byte[] buffer = new byte[12];
		c.insert("Hello", buffer, 2, 0, 8);
		byte[] expected = new byte[]{0, 0, 8, 5, 'H', 'e', 'l', 'l', 'o', 0, 0, 0};
		Assert.assertArrayEquals(expected, buffer);
	}

	@Test
	public void extract1() {
		StringConverter c = new StringConverter();
		byte[] buffer = new byte[]{8, 5, 'H', 'e', 'l', 'l', 'o', 0, 0, 0};
		String str = c.extract(String.class, buffer, 0, 0);
		Assert.assertEquals("Hello", str);
	}

	@Test
	public void extract2() {
		StringConverter c = new StringConverter();
		byte[] buffer = new byte[]{0, 0x08, 0x05, 'H', 'e', 'l', 'l', 'o', 0, 0, 0};
		String str = c.extract(String.class, buffer, 1, 0);
		Assert.assertEquals("Hello", str);
	}

	/**
	 * Test case for #48 (Serialization of long String fails)
	 */
	@Test
	public void extractLongString() {
		String inStr = "myVeryLongStringWithLotsOfWordsAndNumb3ersAndStuff___()xyzäöü123456789" +
				"myVeryLongStringWithLotsOfWordsAndNumb3ersAndStuff_2_()xyzäöü123456789";

		Assert.assertTrue(inStr.length() > 127);
		Assert.assertTrue(inStr.length() < 240);
		byte[] inBytes = inStr.getBytes();

		StringConverter c = new StringConverter();
		byte[] buffer = new byte[256];
		buffer[0] = (byte) 240;
		buffer[1] = (byte) inBytes.length;
		System.arraycopy(inBytes, 0, buffer, 2, inBytes.length);

		String str = c.extract(String.class, buffer, 0, 0);
		Assert.assertEquals(inStr, str);
	}

}
