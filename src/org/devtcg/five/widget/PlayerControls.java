package org.devtcg.five.widget;

import org.devtcg.five.Constants;
import org.devtcg.five.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * This is the main view that resides underneath the player screen's header.
 * Includes album artwork, reflection effect of the artwork, prev/pause/next
 * controls, and progress controls.
 */
public class PlayerControls extends FrameLayout implements OnClickListener,
		OnTouchListener
{
	private Drawable mDivider;

	private ImageView mArtwork;
	private View mProgressControls;
	private View mMainControls;

	private TextView mPlaybackPos;
	private TextView mPlaybackDur;
	private TextView mPlaylistPos;
	private SeekBar mPlaybackInfo;
	private ImageButton mControlPrev;
	private ImageButton mControlPause;
	private ImageButton mControlNext;

	private int mBufferPercent;

	private GestureDetector mGestureDetector;

	private OnClickListener mControlClickListener;

	/**
	 * How many milliseconds to display the progress controls? This timer begins
	 * after a user-initiated gesture or after the playback buffer becomes full.
	 */
	private static final int PROGRESS_CONTROLS_TIMEOUT = 5500;

	/**
	 * Minimum distance a fling must move along the X axis in order
	 * to be considered a gesture to jump to a new playlist position.
	 */
	private static final int MINIMUM_GESTURE_DISTANCE = 50;

	public PlayerControls(Context context)
	{
		this(context, null);
	}

	public PlayerControls(Context context, AttributeSet attrs)
	{
		this(context, attrs, 0);
	}

	public PlayerControls(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);

		mDivider = context.getResources().getDrawable(R.drawable.divider_horizontal_dark);

		LayoutInflater.from(context).inflate(R.layout.player_controls, this);

		mArtwork = (ImageView)findViewById(R.id.album_artwork);
		mProgressControls = findViewById(R.id.progress_controls);
		mMainControls = findViewById(R.id.main_controls);

		mPlaybackPos = (TextView)findViewById(R.id.playback_position);
		mPlaybackDur = (TextView)findViewById(R.id.playback_duration);
		mPlaylistPos = (TextView)findViewById(R.id.playlist_position);

		mPlaybackInfo = (SeekBar)findViewById(R.id.playback_info);
		mPlaybackInfo.setKeyProgressIncrement(10);

		mControlPrev = (ImageButton)findViewById(R.id.control_prev);
		mControlPause = (ImageButton)findViewById(R.id.control_pause);
		mControlNext = (ImageButton)findViewById(R.id.control_next);

		mControlPrev.setOnClickListener(mControlClick);
		mControlPause.setOnClickListener(mControlClick);
		mControlNext.setOnClickListener(mControlClick);

		mGestureDetector = new GestureDetector(mGestureListener);

		setOnClickListener(this);
		setOnTouchListener(this);
	}

	public boolean progressControlsAreShown()
	{
		return mProgressControls.getVisibility() == View.VISIBLE;
	}

	public void hideProgressControls()
	{
		if (progressControlsAreShown())
		{
			mProgressControls.setVisibility(View.GONE);
			invalidate();
		}
	}

	public void showProgressControls()
	{
		if (mBufferPercent < 100)
			showProgressControls(0);
		else
			showProgressControls(PROGRESS_CONTROLS_TIMEOUT);
	}

	public void showProgressControls(int timeout)
	{
		if (!progressControlsAreShown())
		{
			mProgressControls.setVisibility(View.VISIBLE);
			invalidate();
		}

		removeCallbacks(mEventuallyHideProgress);

		if (timeout > 0)
			postDelayed(mEventuallyHideProgress, timeout);
	}

	private final Runnable mEventuallyHideProgress = new Runnable()
	{
		public void run()
		{
			hideProgressControls();
		}
	};

	public void onClick(View v)
	{
		if (!progressControlsAreShown())
			showProgressControls();
		else
			hideProgressControls();
	}

	public boolean onTouch(View v, MotionEvent event)
	{
		return mGestureDetector.onTouchEvent(event);
	}

	public void setBufferPercent(int percent)
	{
		mBufferPercent = percent;
	}

	public void setAlbumCover(Uri uri)
	{
		mArtwork.setImageURI(uri);
	}

	public void setAlbumCover(int resId)
	{
		mArtwork.setImageResource(resId);
	}

	public void setOnControlClickListener(OnClickListener l)
	{
		mControlClickListener = l;
	}

	public SeekBar getSeekBar()
	{
		return mPlaybackInfo;
	}

	public ImageButton getPauseButton()
	{
		return mControlPause;
	}

	private static String formatTime(int sec)
	{
		int minutes = sec / 60;
		int seconds = sec % 60;

		StringBuilder b = new StringBuilder();
		b.append(minutes).append(':');

		if (seconds < 10)
			b.append('0');

		b.append(seconds);

		return b.toString();
	}

	public void setNotPlaying()
	{
		setAlbumCover(R.drawable.not_playing_cover);
		mPlaylistPos.setText("");
		setTrackPosition(0, -1);
	}

	public void setPlaylistPosition(int playlistPos, int playlistSize)
	{
		mPlaylistPos.setText(playlistPos + " of " + playlistSize);
	}

	public void setTrackPosition(int playbackPos, int totalDuration)
	{
		mPlaybackPos.setText(formatTime(playbackPos));

		if (totalDuration >= 0)
			mPlaybackDur.setText('-' + formatTime(totalDuration));
		else
			mPlaybackDur.setText("-:--");

		if (playbackPos == 0)
		{
			mPlaybackInfo.setEnabled(true);
			mPlaybackInfo.setProgress(0);
			mPlaybackInfo.setSecondaryProgress(0);
		}
	}

	private final GestureDetector.SimpleOnGestureListener mGestureListener =
			new GestureDetector.SimpleOnGestureListener()
	{
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
		{
			int dx = (int)(e2.getX() - e1.getX());

			if (Math.abs(dx) > MINIMUM_GESTURE_DISTANCE &&
					Math.abs(velocityX) > Math.abs(velocityY))
			{
				/* Some may consider this logic backward, but I hope to
				 * introduce an animation soon which makes it seem
				 * clearer that you're "dragging" in the next song, not
				 * flicking a directional gesture.  That is, flinging
				 * to the left actually moves to the next song and
				 * vice versa. */
				if (velocityX > 0)
					mControlClickListener.onClick(mControlPrev);
				else
					mControlClickListener.onClick(mControlNext);

				return true;
			}

			return false;
		}
	};

	private final OnClickListener mControlClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			mControlClickListener.onClick(v);
		}
	};

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		/*
		 * First measure the main controls. The album artwork fills the rest of
		 * the available height.
		 */
		measureChildWithMargins(mMainControls, widthMeasureSpec, 0, heightMeasureSpec, 0);

		int width = resolveSize(mMainControls.getMeasuredWidth(), widthMeasureSpec);
		int height = resolveSize(mMainControls.getMeasuredHeight(), heightMeasureSpec);

		/* Fill the remaining space. */
		mArtwork.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.EXACTLY,
				height - mMainControls.getMeasuredHeight()));
		Log.d(Constants.TAG, "Album art measured at " +
				mArtwork.getMeasuredWidth() + "x" + mArtwork.getMeasuredHeight());

		/*
		 * The progress controls "float", and so measurement doesn't depend on
		 * any other factor than its own visibility.
		 */
		if (mProgressControls.getVisibility() != View.GONE)
			measureChildWithMargins(mProgressControls, widthMeasureSpec, 0, heightMeasureSpec, 0);

		setMeasuredDimension(width, height);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom)
	{
		int parentTop = 0;
		int parentBottom = bottom - top;
		int parentLeft = 0;
		int parentRight = right - left;

		mArtwork.layout(parentLeft, parentTop, parentLeft + mArtwork.getMeasuredWidth(),
				parentTop + mArtwork.getMeasuredHeight());
		mMainControls.layout(parentLeft, parentBottom - mMainControls.getMeasuredHeight(),
				parentLeft + mMainControls.getMeasuredWidth(), parentBottom);

		View topView;

		if (mProgressControls.getVisibility() == View.GONE)
			topView = mMainControls;
		else
		{
			topView = mProgressControls;
			mProgressControls.layout(parentLeft,
					mMainControls.getTop() - mProgressControls.getMeasuredHeight(),
					parentLeft + mMainControls.getMeasuredWidth(), mMainControls.getTop());
		}

		mDivider.setBounds(parentLeft, topView.getTop(),
				parentRight, topView.getTop() + mDivider.getIntrinsicHeight());
	}

	@Override
	public void dispatchDraw(Canvas canvas)
	{
		final long drawingTime = getDrawingTime();

		/*
		 * Can't use super.dispatchDraw(canvas) because we need to control the
		 * order that the reflection is drawn. Maybe at some point we can extend
		 * our own ImageView to do this, but for now it's just easier to fix-up
		 * the drawing order manually.
		 */
		drawChild(canvas, mArtwork, drawingTime);

		/* Flip the album artwork. */
		canvas.save();
		canvas.scale(1, -1);
		canvas.translate(0, -(mArtwork.getHeight() * 2));
		mArtwork.draw(canvas);
		canvas.restore();

		if (mProgressControls.getVisibility() == View.VISIBLE)
			drawChild(canvas, mProgressControls, drawingTime);

		drawChild(canvas, mMainControls, drawingTime);

		mDivider.draw(canvas);
	}
}
