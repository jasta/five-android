/*
 * $Id$
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

package org.devtcg.syncml.model;

import org.devtcg.syncml.protocol.SyncItem;

public interface DatabaseMapping
{
	public abstract String getType();
	public abstract String getName();

	public abstract long getLastAnchor();
	public abstract long getNextAnchor();

	public abstract void beginSyncLocal(int type, long last, long next);
	public abstract void beginSyncRemote(int numChnages);
	public abstract void endSync(boolean updateAnchor);

	public abstract int insert(SyncItem item);
	public abstract int update(SyncItem item);
	public abstract int delete(SyncItem item);
}
