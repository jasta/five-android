/*
 * Copyright (C) 2008 Josh Guilfoyle <jasta@devtcg.org>
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

public class DateUtils
{
	public static String formatTimeAgo(long epoch)
	{
		long now = System.currentTimeMillis() / 1000;

		if (now < epoch)
			throw new IllegalArgumentException("Supplied time must be in the past");

		long diff = now - epoch;

		String[] units = { "d", "h", "m" };
		int values[] = { 86400, 3600, 60 };
		int digits[] = { 0, 0, 0 };

		for (int offs = 0; offs < values.length; offs++)
		{    			
			digits[offs] = (int)diff / values[offs];
			diff -= digits[offs] * values[offs];
			
			if (diff == 0)
				break;
		}

		StringBuilder ret = new StringBuilder();
		
		for (int i = 0; i < digits.length; i++)
		{
			if (digits[i] > 0)
				ret.append(digits[i]).append(units[i]).append(' ');
		}
		
		if (ret.length() == 0)
		{
			if (diff > 0)
				ret.append(diff).append("s ago");
			else
				ret.append("now");
		}
		else
			ret.append("ago");

		return ret.toString();
	}
}
