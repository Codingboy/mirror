package de.coding.mirror;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.util.JsonWriter;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.LinkageError;
import java.lang.String;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.coding.mirror.dbstructure.ColumnStructure;
import de.coding.mirror.dbstructure.DBStructure;
import de.coding.mirror.dbstructure.TableStructure;

public class DB extends SQLiteOpenHelper
{
	DBStructure dbStructure;
	private static DB instance;
	long lastTimeMirrored;
	boolean isDelayed;

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
		lastTimeMirrored = -1;
		isDelayed = false;
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
		String sql = "CREATE TABLE IF NOT EXISTS _queue (id INTEGER, mode TEXT, tabel TEXT, data TEXT, timestamp INTEGER, PRIMARY KEY(id, tabel))";
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
		cv.put("tabel", table);
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

	private class MirrorCallback extends AsyncTask<Void, Void, Void>
	{
		Context context;

		MirrorCallback(Context context)
		{
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... params)
		{
			try
			{
				Thread.sleep(Core.getDelay(context));
			}
			catch (InterruptedException e)
			{
				Log.e("Mirror", "error while sleeping", e);
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void param)
		{
			mirror(context, true);
			isDelayed = false;
		}

		@Override
		protected void onPreExecute() {
		}

		@Override
		protected void onProgressUpdate(Void... values) {
		}
	}

	public void mirror(Context context, boolean force)
	{
		long time = System.currentTimeMillis();
		if (!force)
		{
			long delay = Core.getDelay(context);
			if (lastTimeMirrored + delay > time)
			{
				if (!isDelayed)
				{
					isDelayed = true;
					MirrorCallback mirrorCallback = new MirrorCallback(context);
					mirrorCallback.execute(new Void[]{});
				}
				return;
			}
		}
		lastTimeMirrored = time;
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		List<Map<String, List<Map<String, List<String>>>>> content = new LinkedList<>();
		String prevMode = "";
		String prevTable = "";
		while (true)
		{
			Cursor cursor = sqLiteDatabase.query("_queue", null, null, null, null, null, "timestamp ASC");
			if (cursor.getCount() == 0)
			{
				cursor.close();
				break;
			}
			cursor.moveToFirst();
			String mode = cursor.getString(cursor.getColumnIndex("mode"));
			String table = cursor.getString(cursor.getColumnIndex("tabel"));
			String data = cursor.getString(cursor.getColumnIndex("data"));
			int id = cursor.getInt(cursor.getColumnIndex("id"));//TODO
			cursor.close();
			if (prevMode.equals(mode))
			{
				Map<String, List<Map<String, List<String>>>> modeMap = content.get(content.size() - 1);
				List<Map<String, List<String>>> tableList = modeMap.get(mode);
				if (prevTable.equals(table))
				{
					Map<String, List<String>> tableMap = tableList.get(tableList.size()-1);
					List<String> dataList = tableMap.get(table);
					dataList.add(data);
					tableMap.put(table, dataList);
				}
				else
				{
					Map<String, List<String>> tableMap = new HashMap<>();
					List<String> dataList = new LinkedList<>();
					dataList.add(data);
					tableMap.put(table, dataList);
					tableList.add(tableMap);
				}
			}
			else
			{
				Map<String, List<Map<String, List<String>>>> modeMap = new HashMap<>();
				List<Map<String, List<String>>> tableList = new LinkedList<>();
				Map<String, List<String>> tableMap = new HashMap<>();
				List<String> dataList = new LinkedList<>();
				dataList.add(data);
				tableMap.put(table, dataList);
				tableList.add(tableMap);
				modeMap.put(mode, tableList);
				content.add(modeMap);
			}
			prevMode = mode;
			prevTable = table;
		}
		if (content.size() > 0)
		{
			try
			{
				OutputStream out = new ByteArrayOutputStream();
				JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
				writer.beginObject();
				writer.name("uuid").value(Core.getUUID(context));
				writer.name("content");
				writer.beginArray();
				for (Map<String, List<Map<String, List<String>>>> map : content)
				{
					writer.beginObject();
					List<Map<String, List<String>>> list;
					if (map.containsKey("insert"))
					{
						writer.name("insert");
						list = map.get("insert");
					}
					else
					{
						writer.name("delete");
						list = map.get("delete");
					}
					writer.beginArray();
					for (Map<String, List<String>> map1 : list)
					{
						writer.beginObject();
						for (String table : map1.keySet())
						{
							writer.name(table);
							writer.beginArray();
							List<String> dataList = map1.get(table);
							for (String data : dataList)
							{
								JSONObject jsonObject = new JSONObject(data);
								writer.beginObject();
								Iterator<String> iterator = jsonObject.keys();
								while (iterator.hasNext())
								{
									String key = iterator.next();
									Object object = jsonObject.get(key);
									if (object instanceof String)
									{
										writer.name(key).value((String) object);
									}
									else if (object instanceof Integer)
									{
										writer.name(key).value((Integer) object);
									}
								}
								writer.endObject();
							}
							writer.endArray();
						}
						writer.endObject();
					}
					writer.endArray();
					writer.endObject();
				}
				writer.endArray();
				writer.endObject();
				writer.close();
				String json = out.toString();
				String host = Core.getHost(context);
				NetworkCommunication.send(host, json);
				//TODO change status
			}
			catch (Exception e)
			{
				Log.e("Mirror", "error while mirroring", e);
			}
		}
	}
}
