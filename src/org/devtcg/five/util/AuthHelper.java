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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.devtcg.five.provider.util.SourceItem;
import org.devtcg.five.util.streaming.FailfastHttpClient;

public class AuthHelper
{
	public static void setCredentials(FailfastHttpClient client, SourceItem source)
	{
		/*
		 * The password we set here is actually a Base64-encoded SHA1 hash of
		 * the plain text password. In a sense, we're using our own manual
		 * digest authentication instead of the one supported by the HTTP
		 * protocol.
		 */
		client.getCredentialsProvider().setCredentials(
				new AuthScope(source.getHost(), source.getPort()),
				new UsernamePasswordCredentials("fiveuser", source.getPassword()));
	}
}
