package org.devtcg.five.receiver;

import org.devtcg.five.Constants;
import org.devtcg.five.service.IPlaylistService;
import org.devtcg.five.service.PlaylistService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;

public class MediaButton extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

		Log.i(Constants.TAG, "MediaButton: intent=" + intent + ", event=" + event);

		/*
		 * Only process certain keycodes...
		 */
		event = filterKeyEvent(event);
		if (event == null)
			return;

		/*
		 * Only react on ACTION_UP, but consume (via abortBroadcast()) any
		 * actions for these key codes.
		 */
		if (event.getAction() == KeyEvent.ACTION_UP)
		{
			IPlaylistService service = getServiceInstance(context);

			/*
			 * If the service is currently running, invoke commands directly to
			 * it via the Binder. Otherwise, start it and deliver the keyevent
			 * commands. After the service starts, it will call back into our
			 * static handleMediaButtonEvent method.
			 */
			if (service != null)
			{
				try {
					handleMediaButtonEvent(service, event);
				} catch (RemoteException e) {}
			}
			else
			{
				context.startService(new Intent(Intent.ACTION_MEDIA_BUTTON, null,
						context, PlaylistService.class).putExtra(Intent.EXTRA_KEY_EVENT, event));
			}
		}

		/*
		 * Don't let the default music app (or any other) consume this event.
		 */
		abortBroadcast();
	}

	private IPlaylistService getServiceInstance(Context context)
	{
		IBinder binder = peekService(context, new Intent(context, PlaylistService.class));
		if (binder != null)
			return IPlaylistService.Stub.asInterface(binder);
		else
			return null;

	}

	/**
	 * Narrow down the KeyEvent to only those that we intend to handle.
	 */
	private static KeyEvent filterKeyEvent(KeyEvent event)
	{
		if (event == null)
			return null;

		switch (event.getKeyCode())
		{
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			case KeyEvent.KEYCODE_MEDIA_NEXT:
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			case KeyEvent.KEYCODE_MEDIA_STOP:
				return event;
			default:
				return null;
		}
	}

	/**
	 * Handle the media button keycode event provided.
	 *
	 * @throws IllegalArgumentException
	 *             When the provided event cannot be handled. These events are
	 *             expected to have already been filtered by the time this
	 *             method invokes.
	 */
	public static void handleMediaButtonEvent(IPlaylistService service, KeyEvent event)
			throws RemoteException
	{
		if (event == null)
			throw new IllegalArgumentException("event must not be null");

		switch (event.getKeyCode())
		{
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				if (!service.isPlaying())
					service.play();
				else
				{
					if (service.isPaused())
						service.unpause();
					else
						service.pause();
				}
				break;

			case KeyEvent.KEYCODE_MEDIA_NEXT:
				service.next();
				break;

			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				service.previous();
				break;

			case KeyEvent.KEYCODE_MEDIA_STOP:
				service.stop();
				break;
			default:
				throw new IllegalArgumentException("cannot handle media button event=" + event);
		}
	}
}
