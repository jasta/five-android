package org.devtcg.five.music.widget;

import org.devtcg.five.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.animation.AnimationUtils;
import android.widget.SeekBar;
import android.widget.ViewSwitcher;
import android.widget.SeekBar.OnSeekBarChangeListener;

/**
 * Abstraction for the playback position bar shown in the player screen.
 * Defaults the widget to the PlaybackBar style and also adds some
 * flashy effects. 
 */
public class PlaybackBar extends ViewSwitcher
{
	private SeekBar mCurrent;
	private OnSeekBarChangeListener mChangeListener;

	public PlaybackBar(Context ctx)
	{
		super(ctx);
		init();
	}

	public PlaybackBar(Context ctx, AttributeSet attrs)
	{
		super(ctx, attrs);
		init();
	}

	public void init()
	{
		LayoutInflater.from(getContext())
		  .inflate(R.layout.playback_bar, this);

		setInAnimation(AnimationUtils.loadAnimation(getContext(),
		  android.R.anim.fade_in));
		setOutAnimation(AnimationUtils.loadAnimation(getContext(),
		  android.R.anim.fade_out));

		mCurrent = (SeekBar)getCurrentView();
	}

	public void setOnSeekBarChangeListener(OnSeekBarChangeListener l)
	{
		mChangeListener = l;
		mCurrent.setOnSeekBarChangeListener(l);
	}

	public void setMax(int max)
	{
		mCurrent.setMax(max);
	}

	public void setProgress(int progress)
	{
		mCurrent.setProgress(progress);
	}

	public void setSecondaryProgress(int progress)
	{
		mCurrent.setSecondaryProgress(progress);
	}

	public void fadeOutDownloadProgress()
	{
		SeekBar current = (SeekBar)getCurrentView();
		SeekBar hidden = (SeekBar)getNextView();
		hidden.setMax(current.getMax());
		hidden.setProgress(current.getProgress());
		hidden.setSecondaryProgress(0);
		hidden.setEnabled(current.isEnabled());
		hidden.setOnSeekBarChangeListener(mChangeListener);
		
		mCurrent = hidden;
		showNext();
	}
}
