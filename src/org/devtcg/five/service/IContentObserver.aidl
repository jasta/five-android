/*
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

/**
 * Observer to monitor the content retrival process.
 */
interface IContentObserver
{
	/**
	 * Final update sent when the content is finished and available locally
	 * in full.
	 *
	 * @param contentId
	 *   Content identifier.
	 *
	 * @param uri
	 *   Android content URI that can be opened and read or passed
	 *
	 * @param state
	 *   Simple convenience to issue the final progress update.
	 */
	void finished(long contentId, in Uri uri, in ContentState state);

	/** 
	 * Unrecoverable error occurred.  This method is to be replaced by
	 * remote exceptions when they are eventually supported by Android.
	 *
	 * @param contentId
	 *   Content identifier.
	 *
	 * @param message
	 *   Terse error message suitable for display to the user.
	 */
	void error(long contentId, String message);

	/**
	 * Notifies of buffering updates.  Fired per {@link #updateInterval}.
	 *
	 * @param contentId
	 *   Content identifier.
	 *
	 * @param uri
	 *   Cached content URI being populated.  This is provided for
	 *   implementations that wish to begin streaming after a certain
	 *   buffer condition has been met.
	 *
	 * @Param state
	 *   State at the time of this invocation.
	 */
	void updateProgress(long contentId, in Uri uri, in ContentState state);

	/**
	 * User-controllable update intervals.  The observer should satisfy this
	 * function by returning the requested interval for 
	 * {@link #updateProgress}.
	 *
	 * @return
	 *   Update interval in milliseconds.  Recommended interval is 1000ms.
	 */
	long updateInterval();
}
