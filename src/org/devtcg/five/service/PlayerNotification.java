package org.devtcg.five.service;

import org.devtcg.five.Constants;
import org.devtcg.five.R;
import org.devtcg.five.activity.Player;
import org.devtcg.five.util.Song;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * Abstraction to produce the playback notification. This notification is also
 * responsible for setting the foreground state of the service as per the
 * new Android 2.0 semantic changes.
 */
public abstract class PlayerNotification
{
	protected static final int NOTIF_PLAYING = 1;

	public static PlayerNotification getInstance()
	{
		if (Constants.PRE_ECLAIR)
			return PreEclair.Holder.sInstance;
		else
			return EclairAndBeyond.Holder.sInstance;
	}

	protected Notification newNotification(Context context, long songId)
	{
		Notification n = new Notification();

		n.contentIntent = PendingIntent.getActivity(context,
		  0, new Intent(context, Player.class), 0);

		n.flags = Notification.FLAG_NO_CLEAR |
		  Notification.FLAG_ONGOING_EVENT;

		n.icon = R.drawable.stat_notify_musicplayer;
		n.when = System.currentTimeMillis();

		RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.notif_playing);

		Song song = new Song(context.getContentResolver(), songId);

		if (song.albumCover != null)
			view.setImageViewUri(R.id.album_cover, song.albumCover);
		else
			view.setImageViewResource(R.id.album_cover, R.drawable.lastfm_cover_small);

		view.setTextViewText(R.id.song_name, song.title);
		view.setTextViewText(R.id.artist_name, song.artist);
		view.setTextViewText(R.id.album_name, song.album);

		n.contentView = view;

		return n;
	}

	public abstract void showNotification(Service context, long songId);
	public abstract void hideNotification(Service context);

	private static class PreEclair extends PlayerNotification
	{
		private static class Holder
		{
			private static final PreEclair sInstance = new PreEclair();
		}

		private NotificationManager getNotificationManager(Context context)
		{
			return (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		}

		public void showNotification(Service context, long songId)
		{
			context.setForeground(true);
			getNotificationManager(context).notify(NOTIF_PLAYING,
					newNotification(context, songId));
		}

		public void hideNotification(Service context)
		{
			context.setForeground(false);
			getNotificationManager(context).cancel(NOTIF_PLAYING);
		}
	}

	private static class EclairAndBeyond extends PlayerNotification
	{
		private static class Holder
		{
			private static final EclairAndBeyond sInstance = new EclairAndBeyond();
		}

		public void showNotification(Service context, long songId)
		{
			context.startForeground(NOTIF_PLAYING, newNotification(context, songId));
		}

		public void hideNotification(Service context)
		{
			context.stopForeground(true);
		}
	}
}
