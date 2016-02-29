package de.coding.mirror;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class ContentProvider extends android.content.ContentProvider
{
	public static final String AUTHORITY = "de.coding.mirror.ContentProvider";
	public static final String CONTENT_URI_STRING = "content://" + AUTHORITY;
	public static final Uri CONTENT_URI = Uri.parse(CONTENT_URI_STRING);
	DB db;

	@Override
	public Uri insert(Uri uri, ContentValues contentValues)
	{
		String table = uri.getPath().substring(1);
		long id = db.insert(table, contentValues);
		getContext().getContentResolver().notifyChange(Uri.parse(uri.toString() + "/" + id), null);
		return CONTENT_URI.buildUpon().appendPath(table).appendPath(id + "").build();
	}

	@Override
	public int update(Uri uri, ContentValues contentValues, String s, String[] strings)
	{
		return 0;//TODO
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs)
	{
		String table;
		if (uri.getPath().substring(1).contains("/"))
		{
			String[] arr = uri.getPath().substring(1).split("/");
			table = arr[0];
			String id = arr[1];
			where = "_id = ?";
			whereArgs = new String[]{id};
		}
		else
		{
			table = uri.getPath().substring(1);
		}
		int count = db.delete(table, where, whereArgs);
		getContext().getContentResolver().notifyChange(Uri.parse(uri.toString()), null);
		return count;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		String table;
		if (uri.getPath().substring(1).contains("/"))
		{
			String[] arr = uri.getPath().substring(1).split("/");
			table = arr[0];
			String id = arr[1];
			selection = "_id = ?";
			selectionArgs = new String[]{id};//TODO merge
		}
		else
		{
			table = uri.getPath().substring(1);
		}
		Cursor c = db.query(table, projection, selection, selectionArgs, sortOrder);
		c.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
		return c;
	}

	@Override
	public String getType(Uri uri)
	{
		return null;
	}

	@Override
	public boolean onCreate()
	{
		if (db == null)
		{
			db = new DB(getContext(), Core.getDBStructureNumber(getContext()));
		}
		return false;
	}
}
