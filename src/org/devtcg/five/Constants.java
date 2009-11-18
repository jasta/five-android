package org.devtcg.five;

public interface Constants
{
	/**
	 * Global application debug flag.
	 *
	 * @note This flag is not consistently honored. It was introduced well after
	 *       development started.
	 */
	public static final boolean DEBUG = true;

	/**
	 * Generic logging tag to use for various Five components.
	 */
	public static final String TAG = "Five";

	public static final String ACTION_SYNC_BEGIN = "org.devtcg.five.intent.action.SYNC_BEGIN";
	public static final String ACTION_SYNC_END = "org.devtcg.five.intent.action.SYNC_END";

	public static final String EXTRA_SOURCE_ID = "org.devtcg.five.intent.extra.SOURCE_ID";

	/**
	 * Default server port.
	 */
	public static final int DEFAULT_SERVER_PORT = 5545;

	/**
	 * Broadcast to MetaService to begin a sync.
	 */
	public static final String ACTION_START_SYNC = "org.devtcg.five.intent.action.START_SYNC";

	/**
	 * Broadcast to MetaService to request that a currently running sync
	 * cancels.
	 */
	public static final String ACTION_STOP_SYNC = "org.devtcg.five.intent.action.STOP_SYNC";

	/**
	 * Boolean flag honored by Settings to immediately start SourceAdd to ease
	 * the out-of-the-box set-up experience.
	 */
	public static final String EXTRA_START_SOURCE_ADD = "org.devtcg.five.intent.extra.START_SOURCE_ADD";
}
