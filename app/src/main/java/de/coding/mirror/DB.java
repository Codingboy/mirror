package de.coding.mirror;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import de.coding.mirror.dbstructure.ColumnStructure;
import de.coding.mirror.dbstructure.DBStructure;
import de.coding.mirror.dbstructure.TableStructure;

public class DB extends SQLiteOpenHelper
{
	DBStructure dbStructure;
	private static DB instance;

	public static  DB getInstance(Context context, int dbStructure)
	{
		if (instance != null)
		{
			return instance;
		}
		instance = new DB(context, dbStructure);
		return instance;
	}

	private DB(Context context, int dbStructure)
	{
		super(context, "Mirror", null, 1);
		this.dbStructure = DBStructure.getInstance(context, dbStructure);
	}

	@Override
	public void onCreate(SQLiteDatabase sqLiteDatabase)
	{
		for (TableStructure tableStructure : dbStructure.tables)
		{
			StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append("CREATE TABLE IF NOT EXISTS "+tableStructure.name+" (");
			boolean firstColumn = true;
			for (ColumnStructure columnStructure : tableStructure.columns)
			{
				if (!firstColumn)
				{
					stringBuilder.append(", ");
				}
				else
				{
					firstColumn = false;
				}
				stringBuilder.append(columnStructure.name+" "+columnStructure.type);
			}
			stringBuilder.append(", PRIMARY KEY(_id))");
			String sql = stringBuilder.toString();
			Log.i("Mirror", sql);
			sqLiteDatabase.execSQL(sql);
		}
		String sql = "CREATE TABLE IF NOT EXISTS _queue (id INTEGER, mode TEXT, table TEXT, data TEXT, timestamp INTEGER, PRIMARY KEY(id, table))";
		Log.i("Mirror", sql);
		sqLiteDatabase.execSQL(sql);
	}

	@Override
	public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2)
	{
		reset(sqLiteDatabase);
	}

	public void reset(SQLiteDatabase sqLiteDatabase)
	{
		for (TableStructure tableStructure : dbStructure.tables)
		{
			String sql = "DROP TABLE "+tableStructure.name;
			Log.i("Mirror", sql);
			sqLiteDatabase.execSQL(sql);
		}
		String sql = "DROP TABLE _queue";
		Log.i("Mirror", sql);
		sqLiteDatabase.execSQL(sql);
		onCreate(sqLiteDatabase);
	}

	public void reset()
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		reset(sqLiteDatabase);
	}

	public int delete(String table, String where, String[] whereArgs)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		int count = sqLiteDatabase.delete(table, where, whereArgs);
		String mode = dbStructure.getMode(table);
		if (mode.equals("CS"))
		{
			writeToQueue(Integer.valueOf(whereArgs[0]), "delete", table);
		}
		return count;
	}

	public long insert(String table, ContentValues contentValues)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		String mode = dbStructure.getMode(table);
		if (mode.equals("CS"))
		{
			int version = 0;
			if (contentValues.containsKey("_version"))
			{
				version = contentValues.getAsInteger("_version");
			}
			else
			{
				if (contentValues.containsKey("_id"))
				{
					Cursor c = sqLiteDatabase.query(table, new String[]{"_version"}, "_id=?", new String[]{contentValues.getAsInteger("_id") + ""}, null, null, null);
					if (c.getCount() > 0)
					{
						c.moveToFirst();
						version = c.getInt(0);
						version++;
					}
					c.close();
				}
			}
			contentValues.put("_version", version);
			contentValues.put("_status", 0);
		}
		long id = sqLiteDatabase.insertWithOnConflict(table, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
		if (mode.equals("CS"))
		{
			writeToQueue(id, "insert", table);
		}
		return id;
	}

	private void writeToQueue(long id, String mode, String table)
	{
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("{");
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		Cursor cursor = sqLiteDatabase.query(table, null, "_id=?", new String[]{id+""}, null, null, null);
		cursor.moveToFirst();
		for (int i=0; i<cursor.getCount(); i++)
		{
			if (i != 0)
			{
				stringBuilder.append(", ");
			}
			stringBuilder.append("\"");
			stringBuilder.append(cursor.getColumnName(i));
			stringBuilder.append("\": ");
			int type = cursor.getType(i);
			if (type == Cursor.FIELD_TYPE_STRING)
			{
				String value = cursor.getString(i);
				stringBuilder.append("\"");
				stringBuilder.append(value);
				stringBuilder.append("\": ");
			}
			else if (type == Cursor.FIELD_TYPE_INTEGER)
			{
				int value = cursor.getInt(i);
				stringBuilder.append(value);
			}
		}
		cursor.close();
		stringBuilder.append("}");
		String json = stringBuilder.toString();
		ContentValues cv = new ContentValues();
		cv.put("id", id);
		cv.put("mode", mode);
		cv.put("table", table);
		cv.put("data", json);
		cv.put("timestamp", System.currentTimeMillis());
		sqLiteDatabase.insertWithOnConflict("_queue", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
	}

	public int update(String table, ContentValues contentValues, String where, String[] whereArgs)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		String mode = dbStructure.getMode(table);
		if (mode.equals("CS"))
		{
			int version = 0;
			if (contentValues.containsKey("_version"))
			{
				version = contentValues.getAsInteger("_version");
			}
			else
			{
				if (contentValues.containsKey("_id"))
				{
					Cursor c = sqLiteDatabase.query(table, new String[]{"_version"}, "_id=?", new String[]{contentValues.getAsInteger("_id") + ""}, null, null, null);
					if (c.getCount() > 0)
					{
						c.moveToFirst();
						version = c.getInt(0);
						version++;
					}
					c.close();
				}
			}
			contentValues.put("_version", version);
			contentValues.put("_status", 0);
		}
		int count = sqLiteDatabase.updateWithOnConflict(table, contentValues, where, whereArgs, SQLiteDatabase.CONFLICT_REPLACE);
		if (mode.equals("CS"))
		{
			writeToQueue(contentValues.getAsInteger("_id"), "insert", table);
		}
		return count;
	}

	public Cursor query(String table, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		return sqLiteDatabase.query(table, projection, selection, selectionArgs, null, null, sortOrder);
	}

	public void mirror()
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		while (true)
		{
			Cursor cursor = sqLiteDatabase.query("_queue", null, null, null, null, null, "timestamp ASC");
			if (cursor.getCount() == 0)
			{
				cursor.close();
				break;
			}
			cursor.moveToFirst();
			//TODO
			cursor.close();
		}

	}
}
