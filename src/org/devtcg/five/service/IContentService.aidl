/*
 * $Id$ vim:set ft=java:
 *
 * Copyright (C) 2007 Josh Guilfoyle <jasta@devtcg.org>
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

import org.devtcg.five.service.IContentObserver;

/**
 * Responsible for content retrieval through Five.  All content should be
 * accessed through this channel, whether cached or not.  The system will
 * appropriately respond to cache hits and misses and will offer sufficient
 * information to the caller to design sensibly for the user's wait time.
 */
interface IContentService
{
	/**
	 * Access the content object specified.  If necessary, the remote peer
	 * will be contacted and the file downloaded.
	 * <p>
	 * You may use <code>callback</code> to determine when the data is either
	 * partially or totally available on the local device.  Each Five
	 * application is expected to determine if and when the transfer speed is
	 * sufficient to begin streaming.
	 *
	 * @param id
	 *   Local content ID.  Found in any Five provider URI that supports this
	 *   mechanism.
	 *
	 * @param observer
	 *   Observer callback to monitor download progress (if applicable).
	 *   Will also fire when the content is locally available in any case.
	 *   See {@link IContentObserver} for parameters particular to this
	 *   instance.
	 *
	 * @return
	 *   State of the specified content at the time of invocation.  Used as a
	 *   hint to suggest sensible client behaviour when the content is in
	 *   cache.
	 */
	ContentState getContent(long id, in IContentObserver observer);

	/**
	 * Peek at the state of the content.  Is able to determine if the song
	 * would need to (or is currently) downloading, or if it exists in the
	 * cache.
	 *
	 * @param id
	 *
	 * @return
	 *   The state that would be returned had this call been to
	 *   {@link getContent}.
	 */
	ContentState testContent(long id);

	/**
	 * Stop any active download threads operating on this content id.  Please
	 * note that you cannot stop a download that is also being processed by
	 * another connected client.  Reference counting is in place, however, so if
	 * both clients stop the download it will be halted.
	 *
	 * @param id
	 *   Local content ID.
	 */
	void stopDownload(long id);
}
