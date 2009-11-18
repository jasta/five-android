/*
 * $Id$ vim:set ft=java:
 *
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

package org.devtcg.five.service;

interface IPlaylistBufferListener
{
	/**
	 * Fired when changed to the buffer fill percentage are detected.  Playback
	 * stops when fill percent reaches 0, where conversely playback begins when
	 * fill percent reaches 100.
	 */
	void onBufferingUpdate(long songId, int bufferPercent);
}
