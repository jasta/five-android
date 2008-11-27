package org.devtcg.five.music.activity;

import java.util.Random;

import org.devtcg.five.R;
import org.devtcg.five.music.util.Song;
import org.devtcg.five.music.widget.BetterReflectionLayout;
import org.devtcg.five.provider.Five;

import android.R.anim;
import android.app.Activity;
import android.content.ContentUris;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.MediaController.MediaPlayerControl;

public class BetterReflectionLayoutDemo extends Activity
  implements MediaPlayerControl
{
	public static final String TAG = "BetterReflectionLayoutDemo";

	private TextView mArtist;
	private TextView mAlbum;
	private TextView mSong;

	private BetterReflectionLayout mAlbumCover;
	private View mProgress;
//	private ImageView mAlbumCover;
	private MediaController mMediaController;
	
	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.reflection_layout_demo);

		mArtist = (TextView)findViewById(R.id.artist_name);
		mAlbum = (TextView)findViewById(R.id.album_name);
		mSong = (TextView)findViewById(R.id.song_name);

		mAlbumCover = (BetterReflectionLayout)findViewById(R.id.album_cover);
		mProgress = mAlbumCover.findViewById(R.id.progress_controls);
//		mAlbumCover = (ImageView)findViewById(R.id.album_cover);

//		mMediaController = new MediaController(this);
//		mMediaController.setMediaPlayer(this);
//		mMediaController.setAnchorView(mAlbumCover);
//		mMediaController.setEnabled(true);

		mAlbumCover.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				int vis = mProgress.getVisibility() == View.VISIBLE ?
				  View.GONE : View.VISIBLE;
				mProgress.setVisibility(vis);
				mAlbumCover.requestLayout();
				mAlbumCover.invalidate();
//				mMediaController.show();
			}
		});

		SeekBar bar = (SeekBar)findViewById(R.id.playback_info);
		bar.setSecondaryProgress(75);

		setPlaying();
	}
	
	public void setPlaying()
	{
		Uri songsUri = Five.Music.Songs.CONTENT_URI;
		
		Cursor c = getContentResolver().query(songsUri,
		  new String[] { Five.Music.Songs._ID }, null, null, null);
		
		try {
			int pos = (new Random()).nextInt(c.getCount());
			
			if (c.moveToPosition(pos) == false)
				throw new RuntimeException("What the fuck?");
			
			setPlaying(c.getLong(0));
		} finally {
			c.close();
		}
	}
	
	public void setPlaying(long songId)
	{
		Log.d(TAG, "setNowPlaying(" + songId + ")");

		Song song = new Song(this, songId);
		
		if (song.albumId < 0)
			throw new IllegalArgumentException("songId doesn't have an associated album!");

		if (song.albumCoverBig != null)
			mAlbumCover.setImageURI(song.albumCoverBig);
		else
			mAlbumCover.setImageResource(R.drawable.lastfm_cover);

		mSong.setText(song.title);
		mArtist.setText(song.artist);
		mAlbum.setText(song.album);	
	}

	public int getBufferPercentage()
    {
	    // TODO Auto-generated method stub
	    return 0;
    }

	public int getCurrentPosition()
    {
	    // TODO Auto-generated method stub
	    return 0;
    }

	public int getDuration()
    {
	    // TODO Auto-generated method stub
	    return 0;
    }

	public boolean isPlaying()
    {
	    // TODO Auto-generated method stub
	    return false;
    }

	public void pause()
    {
	    // TODO Auto-generated method stub
	    
    }

	public void seekTo(int pos)
    {
	    // TODO Auto-generated method stub
	    
    }

	public void start()
    {
	    // TODO Auto-generated method stub
	    
    }
}
