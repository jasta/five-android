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

/**
 * Responsible for cache management of content distributed through Five.  This
 * service is designed to be a flexible, efficient API allowing the caller to
 * generally manage content for itself.  Locking is not currently managed.
 *
 * For effective use, this service will need to be combined with the main Five
 * content provider.
 *
 * @see Five#Content
 */
interface ICacheService
{
	CachePolicy getStoragePolicy();
	boolean setStoragePolicy(in CachePolicy p);

	/**
	 * Allocate storage space to store cached content.  The specific behaviour
	 * is affected by the current cache policy.  As a convenience, a file
	 * descriptor will be returned as if the caller invoked openFile() on the
	 * Five content provider..
	 *
	 * @return
	 *   On success, a file descriptor intended to store the cached entry for
	 *   the requested content; otherwise, null.
	 */
	ParcelFileDescriptor requestStorage(long sourceId, long contentId);

	/**
	 * Inform the cache manager that an entry can be purged.  This may not
	 * result in an immediate release of resources on the storage card
	 * depending on the current storage policy.  Likewise, cached entries
	 * may be purged automatically by the cache manager without ever having
	 * been released.
	 *
	 * This method is provided primarily to abort storage requests after
	 * they have been made.
	 *
	 * @return
	 *   True if the cached entry was found and released; false otherwise.
	 *   This method cannot error.
	 */
	boolean releaseStorage(long sourceId, long contentId);
}
