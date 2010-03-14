package org.devtcg.five.util;

import java.lang.ref.WeakReference;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

/**
 * Asynchronous bitmap decode helper. Modeled after
 * {@link android.content.AsyncQueryHandler}.
 */
public abstract class AsyncBitmapHandler extends Handler
{
	private static final String TAG = AsyncBitmapHandler.class.getSimpleName();

	private static final boolean DEBUG_MESSAGES = false;

	private final WeakReference<ContentResolver> mResolver;

	private final WorkerHandler mWorkerThreadHandler;

	private static Looper sLooper;

	public AsyncBitmapHandler(ContentResolver cr)
	{
		mResolver = new WeakReference<ContentResolver>(cr);

		synchronized (AsyncBitmapHandler.class)
		{
			if (sLooper == null)
			{
				HandlerThread thread = new HandlerThread("AsyncBitmapWorker",
						Process.THREAD_PRIORITY_BACKGROUND);
                thread.start();

                sLooper = thread.getLooper();
			}
		}

		mWorkerThreadHandler = new WorkerHandler(sLooper);
	}

	public void cancelOperations()
	{
		/*
		 * It isn't documented, but removing with token=null actually removes
		 * all messages.
		 */
		mWorkerThreadHandler.removeCallbacksAndMessages(null);
	}

	public void cancelOperation(int token)
	{
		mWorkerThreadHandler.removeMessages(token);
	}

	public void startDecode(int token, Object cookie, Uri uri)
	{
		Message message = mWorkerThreadHandler.obtainMessage(token, WorkerHandler.MSG_DECODE_URI, 0);

		WorkerArgs args = new WorkerArgs(this, cookie);
		args.uri = uri;
		message.obj = args;

		message.sendToTarget();

		if (DEBUG_MESSAGES)
			Log.d(TAG, "Sent request: token=" + token + ", msgType=" + message.arg1);
	}

	protected abstract void onDecodeComplete(int token, Object cookie, Bitmap result);

	@Override
	public void handleMessage(Message msg)
	{
		WorkerArgs args = (WorkerArgs)msg.obj;

		int token = msg.what;
		int msgType = msg.arg1;

		if (DEBUG_MESSAGES)
			Log.d(TAG, "Received reply: token=" + token + ", msgType=" + msgType);

		switch (msgType)
		{
			case WorkerHandler.MSG_DECODE_URI:
				onDecodeComplete(token, args.cookie, (Bitmap)args.result);
				break;
		}
	}

	/**
	 * Handler attached to the worker thread.
	 */
	private class WorkerHandler extends Handler
	{
		private static final int MSG_DECODE_URI = 0;

		public WorkerHandler(Looper looper)
		{
			super(looper);
		}

		@Override
		public void handleMessage(Message msg)
		{
			ContentResolver resolver = mResolver.get();

			WorkerArgs args = (WorkerArgs)msg.obj;

			int token = msg.what;
			int msgType = msg.arg1;

			if (DEBUG_MESSAGES)
				Log.d(TAG, "Received request: token=" + token + ", msgType=" + msgType);

			switch (msgType)
			{
				case MSG_DECODE_URI:
					if (resolver != null && args.uri != null)
					{
						try {
							args.result = BetterBitmapFactory.decodeUri(resolver, args.uri);
						} catch (OutOfMemoryError e) {
							/* Ignore... */
						}
					}
					break;
			}

			/*
			 * Send the response back to the handler that sent the request
			 * (which is the outer AsyncBitmapHandler).
			 */
			Message reply = args.handler.obtainMessage(token, msgType, 0);
			reply.obj = args;
			reply.sendToTarget();

			if (DEBUG_MESSAGES)
				Log.d(TAG, "Sent reply: token=" + token + ", msgType=" + msgType);
		}
	}

	private static class WorkerArgs
	{
		public Handler handler;
		public Object cookie;

		public Uri uri;

		public Object result;

		public WorkerArgs(Handler handler, Object cookie)
		{
			this.handler = handler;
			this.cookie = cookie;
		}
	}
}
