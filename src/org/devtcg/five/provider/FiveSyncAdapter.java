package org.devtcg.five.provider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.devtcg.five.R;
import org.devtcg.five.meta.data.Protos;
import org.devtcg.five.provider.AbstractTableMerger.SyncableColumns;
import org.devtcg.five.provider.util.SourceItem;
import org.devtcg.five.service.SyncContext;
import org.devtcg.five.service.SyncContext.CancelTrigger;
import org.devtcg.five.util.AuthHelper;
import org.devtcg.five.util.DbUtils;
import org.devtcg.five.util.streaming.FailfastHttpClient;
import org.devtcg.util.IOUtilities;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.google.protobuf.CodedInputStream;

public class FiveSyncAdapter extends AbstractSyncAdapter
{
	private static final FailfastHttpClient mClient = FailfastHttpClient.newInstance(null);

	private static final String RANGE_HEADER = "Range";
	private static final String CONTENT_RANGE_HEADER = "Content-Range";
	private static final String LAST_MODIFIED_HEADER = "X-Last-Modified";
	private static final String MODIFIED_SINCE_HEADER = "X-Modified-Since";
	private static final String CURSOR_POSITION_HEADER = "X-Cursor-Position";
	private static final String CURSOR_COUNT_HEADER = "X-Cursor-Count";

	private static final String FEED_ARTISTS = "artists";
	private static final String FEED_ALBUMS = "albums";
	private static final String FEED_SONGS = "songs";
	private static final String FEED_PLAYLISTS = "playlists";
	private static final String FEED_PLAYLIST_SONGS = "playlistSongs";

	private static final String TAG = "FiveSyncAdapter";

	private final SourceItem mSource;

	private final ContentValues mTmpValues = new ContentValues();

	public FiveSyncAdapter(Context context, FiveProvider provider)
	{
		super(context, provider);
		mSource = provider.mSource;
	}

	@Override
	public void getServerDiffs(SyncContext context, AbstractSyncProvider serverDiffs)
	{
		if (mSource != ((FiveProvider)serverDiffs).mSource)
			throw new IllegalStateException("What the hell happened here?");

		context.moreRecordsToGet = true;

		/* Source must have been deleted or something? */
		if (mSource.moveToFirst() == false)
			return;

		AuthHelper.setCredentials(mClient, mSource);

		long modifiedSince;

		SQLiteDatabase db = serverDiffs.getDatabase();
		db.beginTransaction();
		try {
			modifiedSince = getServerDiffsImpl(context, serverDiffs, FEED_ARTISTS);
			if (modifiedSince >= 0)
				getImageData(context, serverDiffs, FEED_ARTISTS, modifiedSince);

			modifiedSince = getServerDiffsImpl(context, serverDiffs, FEED_ALBUMS);
			if (modifiedSince >= 0)
				getImageData(context, serverDiffs, FEED_ALBUMS, modifiedSince);

			getServerDiffsImpl(context, serverDiffs, FEED_SONGS);
			getServerDiffsImpl(context, serverDiffs, FEED_PLAYLISTS);
			getServerDiffsImpl(context, serverDiffs, FEED_PLAYLIST_SONGS);

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		/* This is a very naive implementation... */
		if (context.hasCanceled() == false && context.hasError() == false)
			context.moreRecordsToGet = false;
	}

	private long getServerDiffsImpl(SyncContext context, AbstractSyncProvider serverDiffs,
		String feedType)
	{
		if (context.hasError() == true || context.hasCanceled() == true)
			return -1;

		String feedUrl = mSource.getFeedUrl(feedType);
		final HttpGet feeds = new HttpGet(feedUrl);
		final Thread currentThread = Thread.currentThread();

		context.trigger = new CancelTrigger() {
			public void onCancel()
			{
				feeds.abort();
				currentThread.interrupt();
			}
		};

		/* TODO: Optimize with another URI inside the provider. */
		long modifiedSince = getModifiedSinceArgument(serverDiffs, feedType);
		feeds.setHeader(MODIFIED_SINCE_HEADER, String.valueOf(modifiedSince));

		if (context.hasCanceled() == true)
			return -1;

		Log.i(TAG, "Downloading changes from feed=" + feedUrl + ", " +
				"starting at modifiedSince=" + modifiedSince);

		try {
			HttpResponse response = mClient.execute(feeds);

			StatusLine status = response.getStatusLine();
			int statusCode = status.getStatusCode();

			if (statusCode != HttpStatus.SC_OK)
				throw new IOException("HTTP GET failed: " + status);

			for (Header header: response.getAllHeaders())
				System.out.println(header.getName() + ": " + header.getValue());

			System.out.println(" ");

			adjustNewestSyncTime(context, response);

			HttpEntity entity = response.getEntity();
			InputStream in = entity.getContent();
			try {
				CodedInputStream stream = CodedInputStream.newInstance(in);
				int count = stream.readRawLittleEndian32();
				while (count-- > 0 && context.hasCanceled() == false)
				{
					int size = stream.readRawLittleEndian32();
					byte[] recordData = stream.readRawBytes(size);
					Protos.Record record = Protos.Record.parseFrom(recordData);
					switch (record.getType())
					{
						case ARTIST:
							insertArtist(context, serverDiffs, record.getArtist());
							break;

						case ALBUM:
							insertAlbum(context, serverDiffs, record.getAlbum());
							break;

						case SONG:
							insertSong(context, serverDiffs, record.getSong());
							break;

						case PLAYLIST:
							insertPlaylist(context, serverDiffs, record.getPlaylist());
							break;

						case PLAYLIST_SONG:
							insertPlaylistSong(context, serverDiffs, record.getPlaylistSong());
							break;
					}
				}
			} finally {
				IOUtilities.close(in);
				context.trigger = null;
			}
		} catch (IOException e) {
			context.networkError = true;
			context.errorMessage = e.getMessage();
		}

		return modifiedSince;
	}

	private void getImageData(SyncContext context, AbstractSyncProvider serverDiffs,
		String feedType, long modifiedSince)
	{
		if (context.hasError() == true || context.hasCanceled() == true)
			return;

		Uri localFeedUri = getLocalFeedUri(feedType);
		String tablePrefix = (feedType.equals(FEED_ALBUMS) ? "a." : "");
		Cursor newRecords = serverDiffs.query(localFeedUri,
			new String[] { AbstractTableMerger.SyncableColumns._ID,
				AbstractTableMerger.SyncableColumns._SYNC_ID },
			tablePrefix + AbstractTableMerger.SyncableColumns._SYNC_TIME + " > " +
				modifiedSince, null, null);

		Resources res = getContext().getResources();

		int thumbWidth = res.getDimensionPixelSize(R.dimen.image_thumb_width);
		int thumbHeight = res.getDimensionPixelSize(R.dimen.image_thumb_height);

		int fullWidth = res.getDimensionPixelSize(R.dimen.large_artwork_width);
		int fullHeight = res.getDimensionPixelSize(R.dimen.large_artwork_height);

		try {
			while (newRecords.moveToNext() && !context.hasError() && !context.hasCanceled())
			{
				long id = newRecords.getLong(0);
				long syncId = newRecords.getLong(1);

				try {
					Uri localFeedItemUri = ContentUris.withAppendedId(localFeedUri, id);

					if (feedType.equals(FEED_ARTISTS))
					{
						downloadFileAndUpdateProvider(context, serverDiffs,
								mSource.getImageUrl(feedType, syncId, thumbWidth, thumbHeight),
								Five.makeArtistPhotoUri(id), localFeedItemUri,
								Five.Music.Artists.PHOTO);
					}
					else if (feedType.equals(FEED_ALBUMS))
					{
						downloadFileAndUpdateProvider(context, serverDiffs,
								mSource.getImageUrl(feedType, syncId, thumbWidth, thumbHeight),
								Five.makeAlbumArtworkUri(id), localFeedItemUri,
								Five.Music.Albums.ARTWORK);
						downloadFileAndUpdateProvider(context, serverDiffs,
								mSource.getImageUrl(feedType, syncId, fullWidth, fullHeight),
								Five.makeAlbumArtworkBigUri(id), localFeedItemUri,
								Five.Music.Albums.ARTWORK_BIG);
					}
				} catch (IOException e) {
					context.networkError = true;
					context.errorMessage = e.getMessage();
				} finally {
					context.trigger = null;
				}
			}
		} finally {
			newRecords.close();
		}
	}

	/**
	 * Issue an HTTP GET request and store the result in a content provider.
	 * Also triggers an update to <code>localFeedItemUri</code>, storing
	 * <code>localUri</code> in <code>columnToUpdate</code>.
	 */
	private static void downloadFileAndUpdateProvider(SyncContext context,
			AbstractSyncProvider serverDiffs, String httpUrl, Uri localUri, Uri localFeedItemUri,
			String columnToUpdate) throws IOException
	{
		if (context.hasError() || context.hasCanceled())
			return;

		final HttpGet request = new HttpGet(httpUrl);

		final Thread currentThread = Thread.currentThread();

		context.trigger = new CancelTrigger() {
			public void onCancel()
			{
				request.abort();
				currentThread.interrupt();
			}
		};

		HttpResponse response = mClient.execute(request);

		if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
			response.getEntity().consumeContent();
		else
		{
			if (context.hasCanceled() == true)
				return;

			/*
			 * Access a temp file path (FiveProvider treats this as a
			 * special case when isTemporary is true and uses a temporary
			 * path to be moved manually during merging).
			 */
			ParcelFileDescriptor pfd;
			try {
				pfd = serverDiffs.openFile(localUri, "w");
			} catch (FileNotFoundException e) {
				response.getEntity().consumeContent();
				throw e;
			}

			InputStream in = response.getEntity().getContent();
			OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pfd);

			try {
				IOUtilities.copyStream(in, out);

				if (context.hasCanceled() == true)
					return;

				/*
				 * Update the record to reflect the newly downloaded uri.
				 * During table merging we'll need to move the file and
				 * update the uri we store here.
				 */
				ContentValues values = new ContentValues();
				values.put(columnToUpdate, localUri.toString());
				serverDiffs.update(localFeedItemUri, values, null, null);
			} finally {
				if (in != null)
					IOUtilities.close(in);

				if (out != null)
					IOUtilities.close(out);
			}
		}
	}

	private void adjustNewestSyncTime(SyncContext context, HttpResponse response)
	{
		Header header = response.getLastHeader(LAST_MODIFIED_HEADER);
		if (header == null)
			return;

		try {
			long lastModified = Long.parseLong(header.getValue());
			context.newestSyncTime = Math.max(context.newestSyncTime, lastModified);
		} catch (NumberFormatException e) {
			Log.w(TAG, "Couldn't understand " + LAST_MODIFIED_HEADER + " response header");
		}
	}

	private static Uri getLocalFeedUri(String feedType)
	{
		if (feedType.equals(FEED_ARTISTS))
			return Five.Music.Artists.CONTENT_URI;
		else if (feedType.equals(FEED_ALBUMS))
			return Five.Music.Albums.CONTENT_URI;
		else if (feedType.equals(FEED_SONGS))
			return Five.Music.Songs.CONTENT_URI;
		else if (feedType.equals(FEED_PLAYLISTS))
			return Five.Music.Playlists.CONTENT_URI;
		else if (feedType.equals(FEED_PLAYLIST_SONGS))
			return Five.Music.PlaylistSongs.CONTENT_URI;

		throw new IllegalArgumentException();
	}

	private long getModifiedSinceArgument(AbstractSyncProvider serverDiffs, String feedType)
	{
		Uri localFeedUri = getLocalFeedUri(feedType);

		/**
		 * First check if the sync provider instance already has some entries
		 * populated from a previously interrupted sync. If yes, the greatest
		 * _SYNC_TIME of those records is considered our next starting point;
		 * otherwise, look for the latest record in the main provider. If no
		 * records are present, assume this is first-time sync and start with 0.
		 * <p>
		 * TODO: This query sucks, we need to issue something that effectively
		 * does SELECT MAX(_SYNC_TIME).
		 */
		String[] projection = new String[] { SyncableColumns._SYNC_TIME };
		String orderBy = SyncableColumns._SYNC_TIME + " DESC";

		long maxSyncTime = DbUtils.cursorForLong(serverDiffs.query(localFeedUri,
				projection, null, null, orderBy), -1);

		if (maxSyncTime < 0)
		{
			/* Check with the real thing. */
			maxSyncTime = DbUtils.cursorForLong(getContext().getContentResolver().query(
					localFeedUri, projection, null, null, orderBy), -1);

			/*
			 * Ok fine, no records have been synced, so start at 0 (which
			 * fetches them all).
			 */
			if (maxSyncTime < 0)
				return 0;
		}

		return maxSyncTime;
	}

	private void insertArtist(SyncContext context, AbstractSyncProvider serverDiffs,
		Protos.Artist artist)
	{
		ContentValues values = mTmpValues;
		values.clear();
		values.put(Five.Music.Artists._SYNC_ID, artist.getId());
		values.put(Five.Music.Artists._SYNC_TIME, artist.getSyncTime());
		values.put(Five.Music.Artists.MBID, artist.getMbid());
		values.put(Five.Music.Artists.NAME, artist.getName());
		values.put(Five.Music.Artists.DISCOVERY_DATE, artist.getDiscoveryDate());
		serverDiffs.insert(Five.Music.Artists.CONTENT_URI, values);
	}

	private void insertAlbum(SyncContext context, AbstractSyncProvider serverDiffs,
		Protos.Album album)
	{
		ContentValues values = mTmpValues;
		values.clear();
		values.put(Five.Music.Albums._SYNC_ID, album.getId());
		values.put(Five.Music.Albums._SYNC_TIME, album.getSyncTime());
		values.put(Five.Music.Albums.MBID, album.getMbid());
		values.put(Five.Music.Albums.ARTIST_ID, album.getArtistId());
		values.put(Five.Music.Albums.NAME, album.getName());
		values.put(Five.Music.Albums.DISCOVERY_DATE, album.getDiscoveryDate());
		values.put(Five.Music.Albums.RELEASE_DATE, album.getReleaseDate());
		serverDiffs.insert(Five.Music.Albums.CONTENT_URI, values);
	}

	private void insertSong(SyncContext context, AbstractSyncProvider serverDiffs,
		Protos.Song song)
	{
		ContentValues values = mTmpValues;
		values.clear();
		values.put(Five.Music.Songs._SYNC_ID, song.getId());
		values.put(Five.Music.Songs._SYNC_TIME, song.getSyncTime());
		values.put(Five.Music.Songs.SOURCE_ID, mSource.getId());
		values.put(Five.Music.Songs.MBID, song.getMbid());
		values.put(Five.Music.Songs.ARTIST_ID, song.getArtistId());
		values.put(Five.Music.Songs.ALBUM_ID, song.getAlbumId());
		values.put(Five.Music.Songs.BITRATE, song.getBitrate());
		values.put(Five.Music.Songs.LENGTH, song.getLength());
		values.put(Five.Music.Songs.TITLE, song.getTitle());
		values.put(Five.Music.Songs.TRACK, song.getTrack());
		values.put(Five.Music.Songs.MIME_TYPE, song.getMimeType());
		values.put(Five.Music.Songs.SIZE, song.getFilesize());

		serverDiffs.insert(Five.Music.Songs.CONTENT_URI, values);
	}

	private void insertPlaylist(SyncContext context, AbstractSyncProvider serverDiffs,
		Protos.Playlist playlist)
	{
		ContentValues values = mTmpValues;
		values.clear();
		values.put(Five.Music.Playlists._SYNC_ID, playlist.getId());
		values.put(Five.Music.Playlists._SYNC_TIME, playlist.getSyncTime());
		values.put(Five.Music.Playlists.NAME, playlist.getName());
		values.put(Five.Music.Playlists.CREATED_DATE, playlist.getCreatedDate());
		serverDiffs.insert(Five.Music.Playlists.CONTENT_URI, values);
	}

	private void insertPlaylistSong(SyncContext context, AbstractSyncProvider serverDiffs,
		Protos.PlaylistSong playlistSong)
	{
		ContentValues values = mTmpValues;
		values.clear();
		values.put(Five.Music.PlaylistSongs._SYNC_ID, playlistSong.getId());
		values.put(Five.Music.PlaylistSongs._SYNC_TIME, playlistSong.getSyncTime());
		values.put(Five.Music.PlaylistSongs.PLAYLIST_ID, playlistSong.getPlaylistId());
		values.put(Five.Music.PlaylistSongs.POSITION, playlistSong.getPosition());
		values.put(Five.Music.PlaylistSongs.SONG_ID, playlistSong.getSongId());
		serverDiffs.insert(Five.Music.PlaylistSongs.CONTENT_URI, values);
	}
}
