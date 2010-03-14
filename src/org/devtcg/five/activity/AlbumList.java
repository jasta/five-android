package org.devtcg.five.activity;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.AlbumItem;
import org.devtcg.five.widget.AbstractMainItemAdapter;
import org.devtcg.five.widget.AbstractMainListActivity;
import org.devtcg.five.widget.MainItemHolder;

import android.content.Context;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;

public class AlbumList extends AbstractMainListActivity
{
	public static void show(Context context)
	{
		context.startActivity(new Intent(context, AlbumList.class));
	}

	@Override
	protected AlbumAdapter createListAdapter()
	{
		return new AlbumAdapter(this, new QueryProvider("a." + Five.Music.Albums.NAME,
				Five.Music.Albums.CONTENT_URI_COMPLETE));
	}

	@Override
	protected AlbumAdapter getAdapter()
	{
		return (AlbumAdapter)mAdapter;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id)
	{
		SongList.show(this, getAdapter().getItemDAO(position));
	}

	private class AlbumAdapter extends AbstractMainItemAdapter<Holder, AlbumItem>
	{
		private AlbumAdapter(Context context, FilterQueryProvider provider)
		{
			super(context, R.layout.album_list_item, provider);
		}

		@Override
		protected void onAttachItemDAO(Cursor cursor)
		{
			mItemDAO = new AlbumItem(cursor);
		}

		protected Uri getCurrentRowBadgeUri()
		{
			return mItemDAO.getArtworkThumbUri();
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			super.bindView(view, context, cursor);

			Holder holder = getHolder(view);

			mItemDAO.getFullName(holder.nameBuffer);
			holder.nameView.setText(holder.nameBuffer.data, 0, holder.nameBuffer.sizeCopied);
			mItemDAO.getArtist(holder.artistBuffer);
			holder.artistView.setText(holder.artistBuffer.data, 0, holder.artistBuffer.sizeCopied);
		}

		@Override
		protected Holder newHolder(View view)
		{
			return new Holder(view);
		}
	}

	private class Holder extends MainItemHolder
	{
		public final TextView nameView;
		public final TextView artistView;

		public final CharArrayBuffer nameBuffer = new CharArrayBuffer(64);
		public final CharArrayBuffer artistBuffer = new CharArrayBuffer(64);

		public Holder(View view)
		{
			super(view, R.drawable.albumart_mp_unknown);
			nameView = (TextView)view.findViewById(R.id.album_name);
			artistView = (TextView)view.findViewById(R.id.artist_name);
		}
	}
}
