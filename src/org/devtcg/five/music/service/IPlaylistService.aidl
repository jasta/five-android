/*
 * $Id$ vim:set ft=java:
 *
 * Copyright (C) 2008 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package org.devtcg.five.music.service;

import org.devtcg.five.music.service.IPlaylistBufferListener;
import org.devtcg.five.music.service.IPlaylistChangeListener;
import org.devtcg.five.music.service.IPlaylistDownloadListener;
import org.devtcg.five.music.service.IPlaylistMoveListener;

/**
 * Manage the background playlist and playback service.  The
 * playlist service itself is responsible for simple player controls, playlist
 * management, Five content downloads, and audio playback.
 *
 * Hooks are present for Activity intergration, such as monitoring playlist
 * movement, playback/buffering, and content downloads.
 *
 * Be adviced that playback refers to playlist state and not actual audio
 * output.  Audio output versus content delivery is separated in the listeners
 * and with {@link isOutputting} and {@link isDownloading}.
 */
interface IPlaylistService
{
	/*-***********************************************************************/
	
	/**
	 * Watch the playlist for jump, advance, and song seek.  Also covers start
	 * and, stop, and pause.
	 */
	void registerOnMoveListener(IPlaylistMoveListener l);
	void unregisterOnMoveListener(IPlaylistMoveListener l);

	/**
	 * Advance to the next track in the queue.  If the playlist is exhausted
	 * and repeat is on, this will behave as if <code>jump(0)</code> was
	 * called.
	 *
	 * @return
	 *   New playlist position or -1 if the queue has no more songs left.
	 *
	 * @see jump
	 */
	int next();

	/**
	 * Move to the previous track in the queue.  If the current position is the
	 * first and repeat is on, this will behave as if
	 * <code>jump(getPlaylistLength() - 1)</code> was called.
	 *
	 * @return
	 *   New playlist position or -1 if the queue has no more songs left.
	 *
	 * @see jump
	 */
	int previous();

	/**
	 * Jump to the specified position.  If playback is currently suspended or
	 * off, it will be started after the jump.  Content delivery is canceled.
	 *
	 * @param pos
	 *   Playlist position.  Must be between 0 (inclusive) and
	 *   <code>getPlaylistLength()</code> (exclusive), else a no-op will occur.
	 */
	void jump(int pos);

	/**
	 * Begin playback at the current position.  If there is no current
	 * position, move to the first then begin playback.  
	 *
	 * If playback is already active or paused, seeks to the 0th second.
	 *
	 * Be advised that the semantics here refer more to playlist management
	 * than literal audio output, meaning that the song may begin
	 * download/buffering, not actually playing.
	 */
	void play();

	/**
	 * Pause playback of the current song.  No-op if there is no active
	 * playback.  Content delivery is unaffected.
	 */
	void pause();

	/**
	 * Resume playback at the paused position.  Identical to {@link play} if
	 * not paused and not currently playing; otherwise, a no-op.  Content
	 * delivery is unaffected.
	 */
	void unpause();

	/**
	 * Stops playback of the current song.  No-op if no playback is active.
	 * Content delivery is canceled.
	 */
	void stop();

	/**
	 * Seek within the currently playing song.  No-op if the player is not
	 * playing and not paused.  If paused, playback is not attempted.
	 *
	 * @param pos
	 *   Seek position in milliseconds.  Edge bound by the current playing
	 *   songs expected running time.
	 */
	void seek(long pos);

	/*-***********************************************************************/

	/**
	 * Answers the current or last playing song as a position in the playlist.
	 * The player is said to be unpositioned if it has advanced beyond the end
	 * (with repeat off), or if no playback has occurred since playlist reset.
	 * In this state, a call to {@link play} will jump to the 0th position.
	 *
	 * @return
	 *   The position if the player is positioned; otherwise, -1.
	 */
	int getPosition();

	/**
	 * Answers the currently playing or paused song's seek position.
	 *
	 * @return
	 *   Current position in milliseconds if playing or paused; otherwise, -1.
	 */
	long tell();

	/**
	 * Answers the duration of the song at the current playlist position.
	 *
	 * @return
	 *   The duration in milliseconds if positioned; otherwise, -1.  The return
	 *   value of milliseconds is typically rounded and used only for symmetry
	 *   with <code>tell()</code> and <code>seek()</code>.
	 */
	long getSongDuration();
	
	/**
	 * Answers the current player state.
	 *
	 * @return
	 *   True if playback is active (not necessarily outputting audio) and
	 *   unpaused; false otherwise.
	 */
	boolean isPlaying();

	/**
	 * Answers the current player state.
	 *
	 * @return
	 *   isPlaying() == false
	 */
	boolean isStopped();

	/**
	 * Answers the current player state.
	 *
	 * @return
	 *   True if playback was active, but paused; false otherwise.
	 */
	boolean isPaused();

	/**
	 * Answers if the song at the playlist position is currently downloading.
	 * This method, combined with {@link isOutputting}, is provided to
	 * determine the state of the stream as one of three possible scenarios:
	 *
	 * isDownloading() | isOutputting() | Explanation
	 *
	 * 1 | 0 | Audio streaming is choked waiting on data from the remote peer.
	 * 1 | 1 | Audio is streaming and being actively fed by the remote peer.
	 * 0 | 0 | Audio is playing from local data.
	 *
	 * Do note that this only applies to the current song.  Preemption may also
	 * be responsible for active downloads which would report false here.  The
	 * only way to detect this case is to use {@link registerOnDownloadListener}.
	 */
	boolean isDownloading();
	boolean isOutputting();

	/*-***********************************************************************/

	/**
	 * Access the current playlist.
	 *
	 * @return
	 *   List of song identifiers as specified by {@link Five.Music.Songs._ID}.
	 */
	List getPlaylist();

	/**
	 * Returns a view of the playlist.  This can be useful to efficient access
	 * the songs coming up next.
	 *
	 * @param from
	 *   Low endpoint (inclusive).  Will not be lower bound to 0.
	 * @param to
	 *   High endpoint (exclusive).  Will be upper bound to the playlist length.
	 *
	 * @see getPlaylist
	 */
	List getPlaylistWindow(int from, int to);

	/**
	 * Access the playlist length without retrieving the entire list.
	 */
	int getPlaylistLength();

	/**
	 * Get the song in the playlist at the specified position.  This is similar
	 * to, but more efficient than, calling
	 * <code>getPlaylist().get(pos)</code>.
	 *
	 * @param pos
	 *   Position in the playlist.
	 *
	 * @return
	 *   The song id if <code>pos</code> is within bounds; otherwise, -1.
	 */
	long getSongAt(int pos);

	/**
	 * Check if a particular song is in the current playlist.  This method is
	 * both a convenience and optimization to avoid using {@link getPlaylist}
	 * and searching.
	 *
	 * This method is functionally equivalent to 
	 * <code>getPlaylist().lastIndexOf(songId)</code>.
	 *
	 * @return
	 *   If found, the list position corresponding to <code>songId</code>; 
	 *   otherwise, -1.
	 */
	int getPositionOf(long songId);

	/**
	 * Peek at the next song in the queue, as if {@link next} was called.  The
	 * current random playback mode is taken into consideration.
	 */
	int peekNext();

	/*-***********************************************************************/

	/**
	 * Shuffle the current playlist.  This is unlike {@link setRandom} in that
	 * the playlist is actually reordered.
	 */
	void shuffle();

	/**
	 * Sets the repeat mode.  See {@link PlaylistService.RepeatMode}.
	 */
	void setRepeat(int repeatMode);
	int getRepeat();

	/**
	 * Sets the random mode.  Unlike {@link shuffle}, this does not reorder the
	 * playlist, but merely randomly affects the play order from the user's
	 * perspective.  This order can still be retrieved from {@link peekNext}.
	 */
	void setRandom(boolean random);
	boolean getRandom();

	/*-***********************************************************************/

	/**
	 * Watch the playlist for insertion and removal.  Also advertises full
	 * playlist clear/load.
	 */
	void registerOnChangeListener(IPlaylistChangeListener l);
	void unregisterOnChangeListener(IPlaylistChangeListener l);

	/**
	 * Load the playlist specified in the five-client provider.  This is more
	 * than just a convenience as it allows the playlist service to try to
	 * retain a relationship with an existing saved playlist.  Useful, for
	 * example, if you want an Activity to display whether a particular
	 * playlist is currently playing, even if it has been somehow mutated after
	 * being loaded.
	 *
	 * @param playlistId
	 *   Links to {@link Five.Music.Playlist._ID}.
	 */
	void loadPlaylistRef(long playlistId);

	/**
	 * Get the last playlist ref loaded, if any.
	 *
	 * @return
	 *   Playlist ref, if one was used to populate the current playlist;
	 *   otherwise, -1.
	 */
	long getPlaylistRef();

	/**
	 * Answers whether the loaded playlist is an exact copy of playlist
	 * originally loaded.  Any mutation of the playlist after it was loaded
	 * through this service interface will flag false here.
	 *
	 * @return
	 *   True if a playlist ref both exists and the playlist has not been
	 *   modified since it was loaded; false otherwise.
	 */
	boolean isPlaylistRefLiteral();

	/**
	 * Clear the playlist.  This will stop playback and any active downloads.
	 * The playlist will be left in an unpositioned state.
	 */
	void clear();

	/**
	 * Insert a song before the specified position.  If the player is inactive
	 * and not in a paused state, this will start playback and position the
	 * playlist at the inserted position.
	 *
	 * This is a no-op if <code>pos</code> is out of bounds.
	 */
	void insert(long songId, int pos);

	/**
	 * Convenience for {@link insert} which inserts a song after the current
	 * position.  If the playlist is unpositioned it is identical to
	 * {@link append(long)}.
	 */
	void insertNext(long songId);

	/**
	 * Convenience for {@link insert} which inserts at position -1.
	 */
	void prepend(long songId);

	/**
	 * Convenience for {@link insert} which inserts at position <code>getPlaylistLength() - 1</code>.
	 */
	void append(long songId);

	/**
	 * Remove a song from the playlist.  No-op if <code>pos</code> is out of
	 * bounds.
	 *
	 * @return
	 *   The song id which was removed; otherwise, -1 if <code>pos</code> is
	 *   out of bounds.
	 */
	long remove(int pos);

	/**
	 * Reposition a song in the playlist.
	 *
	 * @param oldpos
	 *   Position of the song.  See {@link getSongAt}.
	 * @param newpos
	 *   Position before which the chosen song should be inserted.  This
	 *   position represents the position prior to removal, so the actual
	 *   position after this operation successfully completes may differ.
	 *
	 * @return
	 *   New position of the moved song, which may not necessarily equal
	 *   <code>newpos</code>.
	 */
	long move(int oldpos, int newpos);

	/*-***********************************************************************/

	/**
	 * Watch the download manager.  This is only related to playlist management
	 * due to the encapsulation provided.  Can observe preemptive downloads as
	 * well as receive more detail on currently playing songs if they are also
	 * downloading.
	 *
	 * Note that there can be multiple downloads active simultaneously on the
	 * same song in certain esoteric cases.
	 *
	 * Under normal conditions, only one download can be active at a time.
	 */
	void registerOnDownloadListener(IPlaylistDownloadListener l);
	void unregisterOnDownloadListener(IPlaylistDownloadListener l);

	/**
	 * Convenient variation of the download manager to watch the currently
	 * playing track as it buffers awaiting playback.  Buffering is the state
	 * where we must wait on the remote peer to fill a sufficient buffer to
	 * begin playback.  So, buffering stops (but downloads continue) when audio
	 * output begins.
	 */
	void registerOnBufferingListener(IPlaylistBufferListener l);
	void unregisterOnBufferingListener(IPlaylistBufferListener l);
}
