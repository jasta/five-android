package org.devtcg.five.provider;

import org.devtcg.five.service.SyncContext;

import android.content.ContentProvider;
import android.content.Context;
import android.database.Cursor;
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
	private static final boolean DEBUG_ENTRIES = false;

	protected final String mTable;
	protected final Uri mTableUri;

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

	public AbstractTableMerger(String table, Uri tableUri)
	{
		mTable = table;
		mTableUri = tableUri;
	}

	public String getTableName()
	{
		return mTable;
	}

	public void merge(Context context, SyncContext syncContext,
		AbstractSyncProvider serverDiffs, AbstractSyncProvider clientDiffs)
	{
		if (serverDiffs != null)
		{
			mergeServerDiffs(context, syncContext, serverDiffs);
			notifyChanges(context);
		}

		if (clientDiffs != null)
			findLocalChanges(context, syncContext, clientDiffs);

		Log.i(TAG, "merge complete");
	}

	private void mergeServerDiffs(Context context, SyncContext syncContext,
		AbstractSyncProvider serverDiffs)
	{
		/* Set containing all local entries so we can merge (insert/update/resolve). */
		Cursor localCursor = context.getContentResolver().query(mTableUri,
			new String[] { SyncableColumns._ID, SyncableColumns._SYNC_TIME,
				SyncableColumns._SYNC_ID }, null, null,
			SyncableColumns._SYNC_ID);

		/* Set containing all diffed entries (to be merged into main provider). */
		Cursor diffsCursor = serverDiffs.query(mTableUri, null, null, null,
			SyncableColumns._SYNC_ID);

		Log.d(TAG, "Beginning merge of " + mTable);

		try {
			int localCount = 0;
			int diffsCount = 0;

			/*
			 * Move it to the first record; our loop below expects it to keep
			 * pace with diffsSet.
			 */
			boolean localSetHasRows = localCursor.moveToFirst();

			int diffsIdColumn = diffsCursor.getColumnIndexOrThrow(SyncableColumns._ID);
			int diffsSyncTimeColumn = diffsCursor.getColumnIndexOrThrow(SyncableColumns._SYNC_TIME);
			int diffsSyncIdColumn = diffsCursor.getColumnIndexOrThrow(SyncableColumns._SYNC_ID);

			/*
			 * Walk the diffs cursor, replaying each change onto the local
			 * cursor. This is a merge.
			 */
			while (diffsCursor.moveToNext() == true)
			{
				String id = diffsCursor.getString(diffsIdColumn);
				long syncTime = diffsCursor.getLong(diffsSyncTimeColumn);
				long syncId = diffsCursor.getLong(diffsSyncIdColumn);

				diffsCount++;

				if (DEBUG_ENTRIES)
					Log.d(TAG, "processing entry #" + diffsCount + ", syncId=" + syncId);

				/* TODO: conflict is not handled yet. */
				MergeOp mergeOp = MergeOp.NONE;

				long localRowId = 0;
				long localSyncTime = -1;
				long localSyncId = -1;

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

					localSyncId = localCursor.getLong(2);

//					/*
//					 * The local side is authoritative and the the diff side
//					 * contains a record we don't have yet. Insert it out of
//					 * this loop.
//					 */
//					if (TextUtils.isEmpty(syncId))
//					{
//						localSyncId = null;
//						break;
//					}

					/* The server has a record that the local database doesn't have. */
					if (syncId < localSyncId)
					{
						if (DEBUG_ENTRIES)
							Log.d(TAG, "local record " + localId + " has _sync_id > server _sync_id " + syncId);

						localSyncId = -1;
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
				if (localSyncId >= 0)
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

//						/*
//						 * If the local side is authoritative, assign the newly
//						 * created row sync id to the local id.
//						 */
//						if (syncId == null)
//							setSyncId(mDb, newId, newId);
						break;
					case UPDATE:
						updateRow(context, serverDiffs, localRowId, diffsCursor);
						syncContext.numberOfUpdates++;
						break;
					default:
						throw new RuntimeException("TODO");
				}
			}

			Log.d(TAG, "Sync complete: processed " + diffsCount + " server entries");
		} catch (Exception e) {
			Log.e(TAG, "Sync failed.", e);
		} finally {
			diffsCursor.close();
			localCursor.close();
		}
	}

//	private void setSyncId(Provider main, long id, long syncId) throws SQLException
//	{
//		DatabaseUtils.execute(main.getConnection(),
//			"UPDATE " + mTable + " SET " + SyncableColumns._SYNC_ID + " = ? " +
//			"WHERE " + SyncableColumns._ID + " = ?",
//			String.valueOf(syncId), String.valueOf(id));
//	}

	private void findLocalChanges(Context context, SyncContext syncContext,
		AbstractSyncProvider clientDiffs)
	{
		throw new UnsupportedOperationException("Client sync is not supported yet");
	}

//	public void findLocalChanges(SyncableProvider clientDiffs, long lastModified)
//		throws SQLException
//	{
//		/* Find all local changes the other side hasn't seen before. */
//		ResultSet set = DatabaseUtils.executeForResult(mDb.getConnection(),
//			"SELECT * FROM " + mTable + " WHERE " + SyncableColumns._SYNC_TIME + " > " +
//				lastModified, (String[])null);
//
//		InsertHelper inserter = new InsertHelper(clientDiffs.getConnection(), mTable,
//			SyncableColumns._ID);
//
//		try {
//			int columnCount = set.getMetaData().getColumnCount();
//
//			/* Copy all local changes into the temporary client diffs provider. */
//			while (set.next())
//			{
//				inserter.prepareForInsert();
//
//				/*
//				 * The _ID column got moved to the end of the inserter, so we
//				 * start our index at 1 here and just avoid copying it in the loop.
//				 */
//				for (int i = 1; i < columnCount; i++)
//					inserter.bind(i, set.getObject(i + 1));
//
//				/* Now copy the _ID. */
//				inserter.bind(columnCount, set.getObject(1));
//
//				inserter.execute();
//			}
//		} finally {
//			set.close();
//			inserter.close();
//		}
//	}

	/**
	 * Called after merge has completed.
	 */
	public abstract void notifyChanges(Context context);

	public abstract void insertRow(Context context, ContentProvider diffs, Cursor diffsCursor);
	public abstract void deleteRow(Context context, ContentProvider diffs, Cursor diffsCursor);
	public abstract void updateRow(Context context, ContentProvider diffs,
		long id, Cursor diffsCursor);

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
