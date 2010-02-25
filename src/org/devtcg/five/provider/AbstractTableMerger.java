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

package org.devtcg.five.provider;

import org.devtcg.five.service.SyncContext;

import android.content.ContentProvider;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

/**
 * This code and design is largely based on Android's AbstractTableMerger, so
 * the copyright header above is probably void. Should be licensed under Apache
 * as per the original.
 */
public abstract class AbstractTableMerger
{
	private static final String TAG = "AbstractTableMerger";

	/**
	 * Print excessive debug of each entry being merged.
	 */
	private static final boolean DEBUG_ENTRIES = true;

	protected final SQLiteDatabase mDb;
	protected final String mTable;
	protected final String mDeletedTable;
	protected final Uri mTableUri;
	protected final Uri mDeletedTableUri;

	public interface SyncableColumns extends BaseColumns
	{
		/**
		 * Timestamp of last modification.
		 */
		public static final String _SYNC_TIME = "_sync_time";

		/**
		 * Sync id corresponding to the main (non-temporary) provider.
		 */
		public static final String _SYNC_ID = "_sync_id";
	}

	public AbstractTableMerger(SQLiteDatabase db, String table, String deletedTable,
			Uri tableUri, Uri deletedTableUri)
	{
		mDb = db;
		mTable = table;
		mTableUri = tableUri;
		mDeletedTable = deletedTable;
		mDeletedTableUri = deletedTableUri;
	}

	protected SQLiteDatabase getDatabase()
	{
		return mDb;
	}

	public void merge(Context context, SyncContext syncContext,
		AbstractSyncProvider serverDiffs, AbstractSyncProvider clientDiffs)
	{
		if (serverDiffs != null)
		{
            if (!mDb.isDbLockedByCurrentThread()) {
                throw new IllegalStateException("this must be called from within a DB transaction");
            }
			mergeServerDiffs(context, syncContext, serverDiffs);
			notifyChanges(context);
		}

		if (clientDiffs != null)
			findLocalChanges(context, syncContext, clientDiffs);
	}

	private void mergeServerDiffs(Context context, SyncContext syncContext,
		AbstractSyncProvider serverDiffs)
	{
		Log.d(TAG, mTable + ": beginning table merge");

		try {
			/*
			 * Step 1: process server intiated deletes. This is done first in
			 * case the id has been re-used by the server (for instance, server
			 * deleted id 1, then inserted a new record to fill that same id).
			 */
			Log.d(TAG, mTable + ": applying server deletions...");
			int deleteCount = mergeServerDeletions(context, syncContext, serverDiffs);

			/*
			 * Step 2: process server initiated inserts and modifications.
			 */
			Log.d(TAG, mTable + ": applying server modifications...");
			int diffCount = mergeServerChanges(context, syncContext, serverDiffs);

			Log.d(TAG, mTable + ": table merge complete, processed " +
					deleteCount + " deletes, " +
					diffCount + " inserts/updates");
		} catch (Exception e) {
			/* Aiiee!! How can we capture this failure? */
			Log.e(TAG, mTable + ": table merge failed!", e);
			syncContext.mergeError = true;
			syncContext.errorMessage = e.toString();
		}
	}

	/**
	 * Merge collected server deletion requests into the main database table.
	 *
	 * @return Number of deletions successfully applied.
	 */
	private int mergeServerDeletions(Context context, SyncContext syncContext,
			AbstractSyncProvider serverDiffs)
	{
		/* Set containing all deleted entries (to be merged into main provider). */
		Cursor deletedCursor = serverDiffs.query(mDeletedTableUri, null, null, null, null);

		try {
			int deleteCount = 0;
			int deletedSyncIdColumn = deletedCursor.getColumnIndexOrThrow(SyncableColumns._SYNC_ID);

			while (deletedCursor.moveToNext())
			{
				mDb.yieldIfContendedSafely();

				long syncId = deletedCursor.getLong(deletedSyncIdColumn);

				/*
				 * Locate the local record and request its deletion. This design
				 * is copied from Android's AbstractTableMerger (as is most of
				 * this class) but I find myself surprised by the relatively
				 * poor efficiency employed here.
				 *
				 * Seems like a manual join could achieve much better
				 * performance here. We could use the local data set queried in
				 * mergeServerDiffs and then order our deleted records and walk
				 * along the two cursors. This introduces overhead for the
				 * common case of few deletes but it could be tuned for use when
				 * the number of deletions reaches some threshold like 20% of
				 * total records.
				 */
				Cursor localCursor = mDb.query(mTable, null,
						SyncableColumns._SYNC_ID + " = ?", new String[] { String.valueOf(syncId) },
						null, null, null);

				try {
					int matches = localCursor.getCount();

					if (matches == 0)
					{
						/*
						 * This might happen if the local side has already
						 * deleted the record prior to syncing. Not a big deal,
						 * but warn just in case.
						 */
						Log.d(TAG, "received deletion request from server for _sync_id " +
								syncId + ", but there is no matching local record.");
					}
					else if (matches > 1)
					{
						/*
						 * This is a much weirder situation. We should have
						 * never permitted a database entry to be inserted with
						 * a _SYNC_ID matching a previous record. This makes no
						 * sense at all and should absolutely never happen.
						 * Server bug? Client bug? Malicious server? Hmm...
						 */
						Log.d(TAG, "multiple records matched delete request for _sync_id " + syncId);
					}

					while (localCursor.moveToNext())
					{
						if (DEBUG_ENTRIES)
						{
							long localId = localCursor.getLong(
									localCursor.getColumnIndexOrThrow(SyncableColumns._ID));
							Log.d(TAG, "deleting local record " + localId + " with _sync_id " + syncId);
						}

						deleteRow(context, serverDiffs, localCursor);
						syncContext.numberOfDeletes++;
						deleteCount++;
					}
				} finally {
					localCursor.close();
				}
			}

			return deleteCount;
		} finally {
			deletedCursor.close();
		}
	}

	/**
	 * Merge collected server inserts and modifications.
	 */
	private int mergeServerChanges(Context context, SyncContext syncContext,
			AbstractSyncProvider serverDiffs)
	{
		/* Set containing all local entries so we can merge (insert/update/resolve). */
		Cursor localCursor = mDb.query(mTable,
			new String[] { SyncableColumns._ID, SyncableColumns._SYNC_TIME,
				SyncableColumns._SYNC_ID }, null, null, null, null,
			SyncableColumns._SYNC_ID);

		/* Set containing all diffed entries (to be merged into main provider). */
		Cursor diffsCursor = serverDiffs.query(mTableUri, null, null, null,
				SyncableColumns._SYNC_ID);

		try {
			int localCount = 0;
			int diffsCount = 0;
			int diffsIdColumn = diffsCursor.getColumnIndexOrThrow(SyncableColumns._ID);
			int diffsSyncTimeColumn = diffsCursor.getColumnIndexOrThrow(SyncableColumns._SYNC_TIME);
			int diffsSyncIdColumn = diffsCursor.getColumnIndexOrThrow(SyncableColumns._SYNC_ID);

			/*
			 * Move it to the first record to match the diffsCursor position
			 * when it enters the loop below.
			 */
			boolean localSetHasRows = localCursor.moveToFirst();

			/*
			 * Walk the diffs cursor, replaying each change onto the local
			 * cursor. This is a merge.
			 */
			while (diffsCursor.moveToNext() == true)
			{
				mDb.yieldIfContendedSafely();

				String id = diffsCursor.getString(diffsIdColumn);
				long syncTime = diffsCursor.getLong(diffsSyncTimeColumn);
				long syncId = diffsCursor.getLong(diffsSyncIdColumn);

				diffsCount++;

				if (DEBUG_ENTRIES)
					Log.d(TAG, "processing entry #" + diffsCount + ", syncId=" + syncId);

				/* TODO: conflict is not handled yet. */
				MergeOp mergeOp = MergeOp.NONE;

				long localRowId = -1;
				long localSyncTime = -1;

				/*
				 * Position the local cursor to align with the diff cursor. The
				 * two cursor "walk together" to determine if entries are new,
				 * updating, or conflicting.
				 */
				while (localSetHasRows == true && localCursor.isAfterLast() == false)
				{
					localCount++;
					long localId = localCursor.getLong(0);

					/*
					 * If the local record doesn't have a _sync_id, then it is
					 * new locally. No need to merge it now.
					 */
					if (localCursor.isNull(2))
					{
						if (DEBUG_ENTRIES)
							Log.d(TAG, "local record " + localId + " has no _sync_id, ignoring");

						localCursor.moveToNext();
						continue;
					}

					long localSyncId = localCursor.getLong(2);

					/* The partial diffs set is ignoring this record, move along. */
					if (syncId > localSyncId)
					{
						localCursor.moveToNext();
						continue;
					}

					/* The server has a record that the local database doesn't have. */
					if (syncId < localSyncId)
					{
						if (DEBUG_ENTRIES)
							Log.d(TAG, "local record " + localId + " has _sync_id > server _sync_id " + syncId);

						localRowId = -1;
					}
					/* The server and the local database both have this record. */
					else /* if (syncId == localSyncId) */
					{
						if (DEBUG_ENTRIES)
							Log.d(TAG, "local record " + localId + " has _sync_id that matches server _sync_id " + syncId);

						localRowId = localId;
						localSyncTime = localCursor.getLong(1);
						localCursor.moveToNext();
					}

					/* We're positioned along with the diffSet. */
					break;
				}

				/*
				 * TODO: We don't handle conflict resolution, we always treat a
				 * server diff as an update locally regardless of what the
				 * values were before!
				 */
				if (localRowId >= 0)
					/* An existing item has changed... */
					mergeOp = MergeOp.UPDATE;
				else
				{
					/* The local database doesn't know about this record yet. */
					if (DEBUG_ENTRIES)
						Log.d(TAG, "remote record " + syncId + " is new, inserting");

					mergeOp = MergeOp.INSERT;
				}

				switch (mergeOp)
				{
					case INSERT:
						insertRow(context, serverDiffs, diffsCursor);
						syncContext.numberOfInserts++;
						break;
					case UPDATE:
						updateRow(context, serverDiffs, localRowId, diffsCursor);
						syncContext.numberOfUpdates++;
						break;
					default:
						throw new RuntimeException("TODO");
				}
			}

			return diffsCount;
		} finally {
			diffsCursor.close();
			localCursor.close();
		}
	}

	private void findLocalChanges(Context context, SyncContext syncContext,
		AbstractSyncProvider clientDiffs)
	{
		throw new UnsupportedOperationException("Client sync is not supported yet");
	}

	/**
	 * Called after merge has completed.
	 */
	public abstract void notifyChanges(Context context);

	/**
	 * Process a server initiated insert by inserting the record into the main
	 * provider.
	 *
	 * @param context
	 * @param diffs
	 *            Temporary content provider holding all sync entries received
	 *            from the server.
	 * @param diffsCursor
	 *            Cursor positioned at a temporary record sent from the server.
	 */
	public abstract void insertRow(Context context, ContentProvider diffs, Cursor diffsCursor);

	/**
	 * Process a server initiated delete by deleting the record from the main
	 * provider.
	 *
	 * @param context
	 * @param diffs
	 *            Temporary content provider holding all sync entries received
	 *            from the server.
	 * @param localCursor
	 *            Cursor positioned at the local record to delete. Unlike
	 *            {@link #insertRow} and {@link #updateRow}, this cursor was
	 *            queried from the main provider not <code>diffs</code>.
	 */
	public abstract void deleteRow(Context context, ContentProvider diffs, Cursor localCursor);

	/**
	 * Process a server initiated modification by applying all columns from the
	 * provided record to the local record specified.
	 *
	 * @param context
	 * @param diffs
	 *            Temporary content provider holding all sync entries received
	 *            from the server.
	 * @param id
	 *            Local record id into which the merge occurs.
	 * @param localCursor
	 *            Cursor positioned at a temporary record sent from the server.
	 */
	public abstract void updateRow(Context context, ContentProvider diffs,
		long id, Cursor diffsCursor);

	/**
	 * Process a server initiated modification which conflicts with local
	 * modifications. This is currently not implemented and not used. For now,
	 * server always wins.
	 */
	public void resolveRow(Context context, ContentProvider main, long id, String syncId,
		Cursor diffsCursor)
	{
		throw new RuntimeException("This table merger does not handle conflicts, but one was detected with id=" + id + ", syncId=" + syncId);
	}

	private enum MergeOp
	{
		NONE, INSERT, UPDATE, CONFLICTED, DELETE
	}
}
