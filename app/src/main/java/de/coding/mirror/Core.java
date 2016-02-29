package de.coding.mirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.ContactsContract;

import java.util.UUID;

public class Core
{
	public static void init(Context context, String host, int dbStructureNumber)
	{
		setHost(context, host);
		setDBStructureNumber(context, dbStructureNumber);
	}

	public static String getUUID(Context context)
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

	public static String getHost(Context context)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		return storage.getString("host", "42");
	}

	public static void setHost(Context context, String host)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = storage.edit();
		editor.putString("host", host);
		editor.apply();
	}

	public static int getDBStructureNumber(Context context)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		return storage.getInt("dbStructureNumber", 42);
	}

	public static void setDBStructureNumber(Context context, int dbStructureNumber)
	{
		SharedPreferences storage = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = storage.edit();
		editor.putInt("dbStructureNumber", dbStructureNumber);
		editor.apply();
	}
}
