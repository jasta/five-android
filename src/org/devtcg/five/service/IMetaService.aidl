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

import org.devtcg.five.service.IMetaObserver;

interface IMetaService
{
	void registerObserver(in IMetaObserver observer);
	void unregisterObserver(in IMetaObserver observer);

	boolean startSync();
	boolean stopSync();
	boolean isSyncing();

	/**
	 * Get a list of the currently syncing sources, as would be reported with
	 * IMetaObserver#beginSync.  Can be useful for quickly reacting to the
	 * service state. 
	 */
	List whichSyncing();
}
