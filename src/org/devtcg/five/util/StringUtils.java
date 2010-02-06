/*
 * Copyright (C) 2010 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package org.devtcg.five.util;

public class StringUtils
{
	static final char[] HEX_CHAR_TABLE = {
		(char) '0', (char) '1', (char) '2', (char) '3',
		(char) '4', (char) '5', (char) '6', (char) '7',
		(char) '8', (char) '9', (char) 'a', (char) 'b',
		(char) 'c', (char) 'd', (char) 'e', (char) 'f'
	};

	public static String byteArrayToHexString(byte[] raw)
	{
		char[] hex = new char[2 * raw.length];
		int index = 0;

		for (byte b : raw)
		{
			int v = b & 0xFF;
			hex[index++] = HEX_CHAR_TABLE[v >>> 4];
			hex[index++] = HEX_CHAR_TABLE[v & 0xF];
		}

		return new String(hex);
	}
}
