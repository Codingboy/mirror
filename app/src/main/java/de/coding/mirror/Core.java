package de.coding.mirror;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.UUID;

public class Core
{
	public static void init(Context context, String host, int dbStructureNumber, int delay)
	{
		setHost(context, host);
		setDBStructureNumber(context, dbStructureNumber);
		setDelay(context, delay);
		Log.i("Mirror", getUUID(context));
		context.startService(new Intent(context, MirrorService.class));
		context.registerReceiver(new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo netInfo = cm.getActiveNetworkInfo();
				if (netInfo != null && netInfo.isConnected())
				{
					Log.d("Mirror", "mirroring due to network connect");
					DB.getInstance(context, Core.getDBStructureNumber(context)).mirror(context, true);
				}
			}
		}, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
	}

	static String getUUID(Context context)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		if (storage.contains("uuid"))
		{
			return storage.getString("uuid", "42");
		}
		else
		{
			String androidId = "" + android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
			Cursor c = context.getContentResolver().query(ContactsContract.Profile.CONTENT_URI, null, null, null, null);
			c.moveToFirst();
			String username = c.getString(c.getColumnIndex("display_name"));
			c.close();
			UUID uuid = new UUID(androidId.hashCode(), username.hashCode());
			String uuidString = uuid.toString();
			SharedPreferences.Editor editor = storage.edit();
			editor.putString("uuid", uuidString);
			editor.apply();
			return uuidString;
		}
	}

	static String getHost(Context context)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		return storage.getString("host", "42");
	}

	static void setHost(Context context, String host)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = storage.edit();
		editor.putString("host", host);
		editor.apply();
	}

	static int getDBStructureNumber(Context context)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		return storage.getInt("dbStructureNumber", 42);
	}

	static void setDBStructureNumber(Context context, int dbStructureNumber)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = storage.edit();
		editor.putInt("dbStructureNumber", dbStructureNumber);
		editor.apply();
	}

	public static void onStart(Context context)
	{
	}

	public static void onStop(Context context)
	{

	}

	public static long getDelay(Context context)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		return storage.getLong("delay", 42);
	}

	static void setDelay(Context context, int delay)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = storage.edit();
		editor.putLong("delay", delay);
		editor.apply();
	}
}
