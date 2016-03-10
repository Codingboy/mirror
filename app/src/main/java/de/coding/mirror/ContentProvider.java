package de.coding.mirror;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import de.coding.mirror.enums.ColumnKeys;

public class ContentProvider extends android.content.ContentProvider
{
	public static final String AUTHORITY = "de.coding.mirror.ContentProvider";
	public static final String CONTENT_URI_STRING = "content://" + AUTHORITY;
	public static final Uri CONTENT_URI = Uri.parse(CONTENT_URI_STRING);

	@Override
	public Uri insert(Uri uri, ContentValues contentValues)
	{
		//TODO change notify format, add insert, _id
		String table = uri.getPath().substring(1);
		DB db = DB.getInstance(getContext(), Core.getDBStructureNumber());
		long id = db.insert(table, contentValues, getContext());
		getContext().getContentResolver().notifyChange(Uri.parse(uri.toString() + "/" + id), null);
		return CONTENT_URI.buildUpon().appendPath(table).appendPath(id + "").build();
	}

	@Override
	public int update(Uri uri, ContentValues contentValues, String where, String[] whereArgs)
	{
		String table;
		if (uri.getPath().substring(1).contains("/"))
		{
			String[] arr = uri.getPath().substring(1).split("/");
			table = arr[0];
			String id = arr[1];
			where = ColumnKeys._id.toString()+"=?";
			whereArgs = new String[]{id};
		}
		else
		{
			table = uri.getPath().substring(1);
		}
		Log.i("Mirror", "delete from "+table);
		DB db = DB.getInstance(getContext(), Core.getDBStructureNumber());
		int count = db.update(table, contentValues, where, whereArgs, getContext());
		getContext().getContentResolver().notifyChange(Uri.parse(uri.toString()), null);
		return count;
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
			where = ColumnKeys._id.toString()+"=?";
			whereArgs = new String[]{id};
		}
		else
		{
			table = uri.getPath().substring(1);
		}
		Log.i("Mirror", "delete from "+table);
		DB db = DB.getInstance(getContext(), Core.getDBStructureNumber());
		int count = db.delete(table, where, whereArgs, getContext());
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
			selection = ColumnKeys._id.toString()+"=?";
			selectionArgs = new String[]{id};//TODO merge
		}
		else
		{
			table = uri.getPath().substring(1);
		}
		DB db = DB.getInstance(getContext(), Core.getDBStructureNumber());
		Cursor cursor = db.query(table, projection, selection, selectionArgs, sortOrder);
		cursor.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
		return cursor;
	}

	@Override
	public String getType(Uri uri)
	{
		return null;
	}

	@Override
	public boolean onCreate()
	{
		return false;
	}
}
