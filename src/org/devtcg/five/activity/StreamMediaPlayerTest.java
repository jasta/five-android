package org.devtcg.five.activity;

import java.io.IOException;

import org.devtcg.five.R;
import org.devtcg.five.util.streaming.DownloadManager;
import org.devtcg.five.util.streaming.DownloadTailStream;
import org.devtcg.five.util.streaming.StreamMediaPlayer;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

public class StreamMediaPlayerTest extends Activity
{
	public static final String TAG = "StreamMediaPlayerTest";
	
	private static final String STREAM_URI = "http://www.theyellowstereo.com/Daily%20Graboid/Kings%20of%20Leon%20-%20Sex%20on%20Fire.mp3";
	private static final String CACHE_PATH = "/sdcard/Kings_of_Leon-Sex_on_Fire.mp3";
	
	private ProgressHandler mHandler = new ProgressHandler();

	private final StreamMediaPlayer mPlayer = new StreamMediaPlayer();

	private boolean mPlaying = false;

	private SeekBar mPlayInfo;
	private Button mDownload;
	private Button mPlay; 

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		setContentView(R.layout.stream_media_player_test);

		mPlayInfo = (SeekBar)findViewById(R.id.playback_info);
		mPlayInfo.setOnSeekBarChangeListener(mSeeked);
		mPlayInfo.setThumb(null);
		
		mDownload = (Button)findViewById(R.id.download);
		mDownload.setOnClickListener(mDownloadClick);

		mPlay = (Button)findViewById(R.id.playstop);
		mPlay.setOnClickListener(mPlayClick);
	}

	@Override
	protected void onDestroy()
	{
		mPlayer.stop();
		mPlayer.reset();
		mPlayer.release();

		super.onDestroy();
	}

	private void setPlaying(boolean playing)
	{
		if (playing == true)
		{
			mPlay.setText("Stop");
			mPlay.setOnClickListener(mStopClick);
			mPlayInfo.setEnabled(true);
		}
		else
		{
			mPlay.setText("Play");
			mPlay.setOnClickListener(mPlayClick);
			mPlayInfo.setEnabled(false);
			mPlayInfo.setProgress(0);
			mPlayInfo.setSecondaryProgress(0);

			mHandler.stopPlaybackMonitoring();
		}

		mPlaying = playing;
	}

	private final OnClickListener mDownloadClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			DownloadManager.Download d;

			if ((d = mManager.lookupDownload(STREAM_URI)) == null)
			{
				try {
					mManager.startDownload(STREAM_URI, CACHE_PATH);
				} catch (IOException e) {
					Log.e(TAG, "Crap", e);
				}
			}
			else
			{
				Toast.makeText(StreamMediaPlayerTest.this,
				  "Download already in progress...", Toast.LENGTH_SHORT)
				    .show();
			}
		}
	};

	private final OnClickListener mPlayClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			StreamMediaPlayer player = mPlayer;
			
			/* I don't understand why this is necessary.  I am cleanly invoking
			 * reset() each time the MediaPlayer is to be returned to
			 * its initial state however it doesn't seem to matter for the case
			 * where it's reset before it had even started playing. */
			player.reset();
			player.setOnBufferingUpdateListener(mBufferListener);
			player.setOnPreparedListener(mPreparedListener);
			player.setOnErrorListener(mErrorListener);
			player.setOnCompletionListener(mCompletionListener);

			DownloadManager.Download dl =
			  mManager.lookupDownload(STREAM_URI);

			if (dl == null)
			{
				try {
					dl = mManager.startDownload(STREAM_URI, CACHE_PATH);
				} catch (IOException e) {
					Log.e(TAG, "Crap", e);
				}
			}

			try {
				player.setDataSource(new DownloadTailStream(dl));
			} catch (Exception e) {
				Log.e(TAG, "Damnit", e);
				Toast.makeText(StreamMediaPlayerTest.this,
				  "Fatal MediaPlayer error: " + e.toString(), Toast.LENGTH_LONG)
				    .show();
				mManager.stopDownload(dl);
				player.reset();
				return;
			}

			setPlaying(true);
			player.prepareAsync();

			Log.i(TAG, "Should be buffering...");
		}
	};

	private final OnClickListener mStopClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			setPlaying(false);

			mManager.stopAllDownloads();

			if (mPlayer.isPlaying() == true)
				mPlayer.stop();

			mPlayer.reset();
		}
	};

	private final SeekBar.OnSeekBarChangeListener mSeeked = new SeekBar.OnSeekBarChangeListener()
	{
		public void onProgressChanged(SeekBar seekBar, int progress,
		  boolean fromTouch)
		{
			if (fromTouch == false)
				return;

			assert mPlaying == true;

			int dur = mPlayer.getDuration();
			int target = (int)((progress / 100f) * dur);
			mPlayer.seekTo(target);
		}

		public void onStartTrackingTouch(SeekBar seekBar)
		{
		}

		public void onStopTrackingTouch(SeekBar seekBar)
		{
		}
	};

	private final MediaPlayer.OnBufferingUpdateListener mBufferListener =
	  new MediaPlayer.OnBufferingUpdateListener()
	{
		public void onBufferingUpdate(MediaPlayer mp, int percent)
		{
			Log.i(TAG, "onBufferingUpdate: percent=" + percent);
		}
	};

	private final MediaPlayer.OnPreparedListener mPreparedListener =
	  new MediaPlayer.OnPreparedListener()
	{
		public void onPrepared(MediaPlayer mp)
		{
			Log.i(TAG, "Should be playing...");
			mp.start();
			mHandler.startPlaybackMonitoring();
		}
	};

	private final MediaPlayer.OnErrorListener mErrorListener =
	  new MediaPlayer.OnErrorListener()
	{
		public boolean onError(MediaPlayer mp, int what, int extra)
		{
			Log.i(TAG, "onError: what=" + what + ", extra=" + extra);

			mManager.stopAllDownloads();

			if (mp.isPlaying() == true)
				mp.stop();

			mp.reset();
			setPlaying(false);

			return true;
		}
	};

	private final MediaPlayer.OnCompletionListener mCompletionListener =
	  new MediaPlayer.OnCompletionListener()
	{
		public void onCompletion(MediaPlayer mp)
		{
			Log.i(TAG, "Should be finished.");
			mp.reset();
			setPlaying(false);
		}
	};
	
	private final DownloadManager mManager = new DownloadManager(this)
	{
		@Override
		public void onStateChange(String url, int state, String message)
		{
			Log.i(TAG, "State change for: " + url + " [state=" + state + ", msg=" + message +"]");
		}

		@Override
		public void onFinished(String url)
		{
			Log.i(TAG, "Download finished for: " + url + "...");
		}

		@Override
		public void onAborted(String url)
		{
			super.onAborted(url);
		}

		@Override
		public void onError(String url, int state, final String err)
		{
			super.onError(url, state, err);

			mHandler.post(new Runnable() {
				public void run() {
					Toast.makeText(StreamMediaPlayerTest.this,
					  "Fatal download error: " + err, Toast.LENGTH_LONG)
					    .show();

					if (mPlaying == true)
					{
						if (mErrorListener.onError(mPlayer, 0, 0) == false)
							mCompletionListener.onCompletion(mPlayer);
					}
				}
			});
		}

		@Override
		public void onProgressUpdate(String url, int percent)
		{
			mHandler.sendDownloadProgress(percent);
		}
	};

	private class ProgressHandler extends Handler
	{
		private static final int MSG_DOWNLOAD_PROGRESS = 1;
		private static final int MSG_PLAYBACK_PROGRESS = 2;

		public int mLastDownloadProgress = -1;

		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			case MSG_DOWNLOAD_PROGRESS:
				mPlayInfo.setSecondaryProgress(msg.arg1);
				break;
			case MSG_PLAYBACK_PROGRESS:
				mPlayInfo.setProgress((int)
				 (((float)mPlayer.getCurrentPosition() /
				  (float)mPlayer.getDuration()) * 100f));
				sendMessageDelayed(obtainMessage(MSG_PLAYBACK_PROGRESS),
				  1000);
				break;
			default:
				super.handleMessage(msg);
				break;
			}
		}

		public void startPlaybackMonitoring()
		{
			removeMessages(MSG_PLAYBACK_PROGRESS);
			sendMessage(obtainMessage(MSG_PLAYBACK_PROGRESS));
		}

		public void stopPlaybackMonitoring()
		{
			removeMessages(MSG_PLAYBACK_PROGRESS);
		}

		public void sendDownloadProgress(int progress)
		{
			sendMessage(obtainMessage(MSG_DOWNLOAD_PROGRESS, progress, -1));
		}
	}
}
