package org.devtcg.five.activity;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.ArtistItem;
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

public class ArtistList extends AbstractMainListActivity
{
	public static void show(Context context)
	{
		context.startActivity(new Intent(context, ArtistList.class));
	}

	@Override
	protected ArtistAdapter createListAdapter()
	{
		return new ArtistAdapter(this, new QueryProvider(Five.Music.Artists.NAME,
				Five.Music.Artists.CONTENT_URI));
	}

	protected ArtistAdapter getAdapter()
	{
		return (ArtistAdapter)mAdapter;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id)
	{
		ArtistAlbumList.show(this, getAdapter().getItemDAO(position));
	}

	private class ArtistAdapter extends AbstractMainItemAdapter<Holder, ArtistItem>
	{
		private ArtistAdapter(Context context, FilterQueryProvider provider)
		{
			super(context, R.layout.artist_list_item, provider);
		}

		@Override
		protected void onAttachItemDAO(Cursor cursor)
		{
			mItemDAO = new ArtistItem(cursor);
		}

		protected Uri getCurrentRowBadgeUri()
		{
			return mItemDAO.getPhotoUri();
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			super.bindView(view, context, cursor);

			Holder holder = getHolder(view);

			mItemDAO.getFullName(holder.nameBuffer);
			holder.nameView.setText(holder.nameBuffer.data, 0, holder.nameBuffer.sizeCopied);
			holder.countView.setText(getArtistCounts(mItemDAO, holder.countBuffer));
		}

		private String getArtistCounts(ArtistItem artist, StringBuilder buffer)
		{
			int nalbums = artist.getNumAlbums();
			int nsongs = artist.getNumSongs();

			buffer.setLength(0);
			buffer.append(nalbums).append(" album");
			if (nalbums != 1)
				buffer.append('s');
			buffer.append(", ");
			buffer.append(nsongs).append(" song");
			if (nsongs != 1)
				buffer.append('s');

			return buffer.toString();
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
		public final TextView countView;

		public final CharArrayBuffer nameBuffer = new CharArrayBuffer(64);
		public final StringBuilder countBuffer = new StringBuilder();

		public Holder(View view)
		{
			super(view, R.drawable.picture_contact_placeholder);
			nameView = (TextView)view.findViewById(R.id.artist_name);
			countView = (TextView)view.findViewById(R.id.artist_counts);
		}
	}
}
