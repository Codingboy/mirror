package de.coding.mirror;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.UUID;

import me.pushy.sdk.Pushy;

public class Core
{
	private static String uuid;
	private static String host;
	private static int dbStructureNumber;
	private static int delay;
	private static String pushyID;
	private static int maxQueueCount;

	/**
	 * Initialises the framework.
	 * @param context context
	 * @param host host
	 * @param dbStructureNumber dbStructureNumber
	 * @param delay delay
	 * @param maxQueueCount maxQueueCount
	 * @post host is initialised
	 * @post dbStructureNumber is initialised
	 * @post delay is initialised
	 * @post maxQueueCount is initialised
	 */
	public static void init(Context context, String host, int dbStructureNumber, int delay, int maxQueueCount)
	{
		setHost(host);
		setDBStructureNumber(dbStructureNumber);
		setDelay(delay);
		setMaxQueueCount(maxQueueCount);
		Log.i("Mirror", getUUID(context));
		notify(context, "uuid", getUUID(context), null);
		//context.startService(new Intent(context, MirrorService.class));
		context.registerReceiver(new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo netInfo = cm.getActiveNetworkInfo();
				if (netInfo != null && netInfo.isConnected())
				{
					new MirrorTask(context).execute(new Void[]{});
				}
			}
		}, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
		Pushy.listen(context);
	}

	/**
	 * Gets a ContentProvider Uri for a content.
	 * @param content table/(id)
	 * @return ContentProvider Uri
	 */
	public static Uri getContentUri(String content)
	{
		return Uri.parse(ContentProvider.CONTENT_URI + "/" + content);
	}

	/**
	 * Registers a ContentObserver at a ContentProvider.
	 * @param context context
	 * @param observer ContentObserver to be registered
	 */
	public static void registerContentObserver(Context context, ContentObserver observer)
	{
		registerContentObserver(context, observer, ContentProvider.CONTENT_URI);
	}

	/**
	 * Registers a ContentObserver at a ContentProvider.
	 * @param context context
	 * @param observer ContentObserver to be registered
	 * @param uri uri
	 */
	public static void registerContentObserver(Context context, ContentObserver observer, Uri uri)
	{
		context.getContentResolver().registerContentObserver(uri, true, observer);
	}

	/**
	 * Gets an int from the current row for a selected column of a cursor.
	 * @param cursor open cursor with a valid row
	 * @param columnName name of a column
	 * @return value of column
	 * @pre columnName is valid column name
	 * @pre cursor able to read data
	 */
	public static int getInt(Cursor cursor, String columnName)
	{
		return cursor.getInt(cursor.getColumnIndex(columnName));
	}

	/**
	 * Gets a String from the current row for a selected column of a cursor.
	 * @param cursor open cursor with a valid row
	 * @param columnName name of a column
	 * @return value of column
	 * @pre columnName is valid column name
	 * @pre cursor able to read data
	 */
	public static String getString(Cursor cursor, String columnName)
	{
		return cursor.getString(cursor.getColumnIndex(columnName));
	}

	/**
	 * Mirrors the databasechanges immediately to the server.
	 * @param context context
	 */
	public void mirror(Context context)
	{
		DB db = DB.getInstance(context, getDBStructureNumber());
		db.mirror(context, true);
	}

	/**
	 * Mirrors the database changes to server.
	 * @pre host needs to be set
	 * @pre uuid needs to be set
	 * @pre delay needs to be set
	 * @post pushyID is initialised
	 */
	private static class MirrorTask extends AsyncTask<Void, Void, Void>
	{
		Context context;

		MirrorTask(Context context)
		{
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... params)
		{
			Log.d("Mirror", "mirroring due to network connect");
			DB.getInstance(context, Core.getDBStructureNumber()).mirror(context, true);
			return null;
		}

		@Override
		protected void onPostExecute(Void param)
		{
		}

		@Override
		protected void onPreExecute()
		{
		}

		@Override
		protected void onProgressUpdate(Void... values)
		{
		}
	}

	/**
	 * Gets the uuid.
	 * @param context context
	 * @return uuid
	 * @post uuid is initialised
	 */
	protected static String getUUID(Context context)
	{
		if (uuid == null)
		{
			String androidId = "" + android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
			Cursor c = context.getContentResolver().query(ContactsContract.Profile.CONTENT_URI, null, null, null, null);
			c.moveToFirst();
			String username = c.getString(c.getColumnIndex("display_name"));
			c.close();
			UUID uuidO = new UUID(androidId.hashCode(), username.hashCode());
			uuid = uuidO.toString();
		}
		return uuid;
	}

	/**
	 * Gets the host.
	 * @return host
	 */
	protected static String getHost()
	{
		return host;
	}

	/**
	 * Sets the host.
	 * @param host host
	 * @post host is initialised
	 */
	protected static void setHost(String host)
	{
		Core.host = host;
	}

	/**
	 * Gets the pushyID.
	 * @return pushyID
	 */
	protected static String getPushyID()
	{
		return pushyID;
	}

	/**
	 * Sets the pushyID.
	 * @param pushyID pushyID
	 * @post pushyID is initialised
	 */
	protected static void setPushyID(String pushyID)
	{
		Core.pushyID = pushyID;
	}

	/**
	 * Gets the dbStructureNumber.
	 * @return dbStructureNumber
	 */
	protected static int getDBStructureNumber()
	{
		return dbStructureNumber;
	}

	/**
	 * Sets the dbStructureNumber.
	 * @param dbStructureNumber dbStructureNumber
	 * @post dbStructureNumber is initialised
	 */
	protected static void setDBStructureNumber(int dbStructureNumber)
	{
		Core.dbStructureNumber = dbStructureNumber;
	}

	/**
	 * Gets the delay.
	 * @return delay
	 */
	protected static int getDelay()
	{
		return delay;
	}

	/**
	 * Sets the delay.
	 * @param delay delay
	 * @post delay is initialised
	 */
	protected static void setDelay(int delay)
	{
		Core.delay = delay;
	}

	/**
	 * Sends a notification.
	 * @param context context
	 * @param title title of the notification
	 * @param message message of the notification
	 * @param intent intent to be called upon click
	 */
	public static void notify(Context context, String title, String message, Intent intent)
	{
		Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
		Notification.Builder mBuilder = new Notification.Builder(context)
						.setSmallIcon(android.R.drawable.ic_dialog_info)
						.setAutoCancel(true)
						.setSound(defaultSoundUri)
						.setContentTitle(title)
						.setContentText(message);
		if (intent != null)
		{
			PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
			mBuilder.setContentIntent(pendingIntent);
		}
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(0, mBuilder.build());

		/*Notification notification = new Notification(android.R.drawable.ic_dialog_info, message, System.currentTimeMillis());
		notification.defaults = Notification.DEFAULT_ALL;
		notification.flags = Notification.FLAG_AUTO_CANCEL;
		notification.setLatestEventInfo(context, title, message, null);
		NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(0, notification);*/
	}

	/**
	 * Gets the maxQueueCount.
	 * @return maxQueueCount
	 */
	protected static int getMaxQueueCount()
	{
		return maxQueueCount;
	}

	/**
	 * Initialises the maxQueueCount.
	 * @param maxQueueCount
	 * @post maxQueueCount is initialised
	 */
	protected static void setMaxQueueCount(int maxQueueCount)
	{
		Core.maxQueueCount = maxQueueCount;
	}
}
