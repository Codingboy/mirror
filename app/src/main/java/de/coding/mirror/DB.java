package de.coding.mirror;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.JsonWriter;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.String;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.coding.mirror.dbstructure.ColumnStructure;
import de.coding.mirror.dbstructure.DBStructure;
import de.coding.mirror.dbstructure.TableStructure;
import me.pushy.sdk.Pushy;
import me.pushy.sdk.exceptions.PushyException;

public class DB extends SQLiteOpenHelper
{
	DBStructure dbStructure;
	private static DB instance;
	long lastTimeMirrored;
	boolean isDelayed;
	boolean isRegistered;

	public static DB getInstance(Context context, int dbStructure)
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
		isRegistered = false;
	}

	@Override
	public void onCreate(SQLiteDatabase sqLiteDatabase)
	{
		for (String k : dbStructure.tables.keySet())
		{
			TableStructure tableStructure = dbStructure.tables.get(k);
			StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append("CREATE TABLE IF NOT EXISTS "+tableStructure.name+" (");
			boolean firstColumn = true;
			for (String key : tableStructure.columns.keySet())
			{
				ColumnStructure columnStructure = tableStructure.columns.get(key);
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
		for (String key : dbStructure.tables.keySet())
		{
			TableStructure tableStructure = dbStructure.tables.get(key);
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

	public int delete(String table, String where, String[] whereArgs, Context context)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		int count = sqLiteDatabase.delete(table, where, whereArgs);
		String mode = dbStructure.tables.get(table).mode;
		if (mode.equals("CS"))
		{
			writeToQueue(Integer.valueOf(whereArgs[0]), "delete", table, context);
		}
		return count;
	}

	public long insert(String table, ContentValues contentValues, Context context)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		String mode = dbStructure.tables.get(table).mode;
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
			writeToQueue(id, "insert", table, context);
		}
		return id;
	}

	private void writeToQueue(long id, String mode, String table, Context context)
	{
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("{");
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		Cursor cursor = sqLiteDatabase.query(table, null, "_id=?", new String[]{id+""}, null, null, null);
		cursor.moveToFirst();
		for (int i=0; i<cursor.getCount(); i++)
		{
			int statusFix = 0;
			for (int j=0; j<cursor.getColumnCount(); j++)
			{
				if (cursor.getColumnName(j).equals("_status"))
				{
					statusFix = 1;
					continue;
				}
				if (j != statusFix)
				{
					stringBuilder.append(", ");
				}
				stringBuilder.append("\"");
				stringBuilder.append(cursor.getColumnName(j));
				stringBuilder.append("\": ");
				int type = cursor.getType(j);
				if (type == Cursor.FIELD_TYPE_STRING)
				{
					String value = cursor.getString(j);
					stringBuilder.append("\"");
					stringBuilder.append(value);
					stringBuilder.append("\"");
				}
				else if (type == Cursor.FIELD_TYPE_INTEGER)
				{
					int value = cursor.getInt(j);
					stringBuilder.append(value);
				}
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
		Log.d("Mirror", "mirroring due to db interaction");
		new MirrorTask(context).execute(new Void[]{});
	}

	private class MirrorTask extends AsyncTask<Void, Void, Void>
	{
		Context context;

		MirrorTask(Context context)
		{
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... params)
		{
			mirror(context, true);
			return null;
		}

		@Override
		protected void onPostExecute(Void param)
		{
		}

		@Override
		protected void onPreExecute() {
		}

		@Override
		protected void onProgressUpdate(Void... values) {
		}
	}

	private class RegisterTask extends AsyncTask<Void, Void, Void>
	{
		Context context;

		RegisterTask(Context context)
		{
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... params)
		{
			try
			{
				String registrationId = Pushy.register(context);
				Log.d("Mirror", registrationId + "");
				Core.setPushyID(registrationId);
				isRegistered = NetworkCommunication.register(context);
				mirror(context, false);
			}
			catch (PushyException e)
			{
				Log.e("Mirror", "error while registering", e);
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void param)
		{
		}

		@Override
		protected void onPreExecute() {
		}

		@Override
		protected void onProgressUpdate(Void... values) {
		}
	}

	public int update(String table, ContentValues contentValues, String where, String[] whereArgs, Context context)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		String mode = dbStructure.tables.get(table).mode;
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
			writeToQueue(contentValues.getAsInteger("_id"), "update", table, context);
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
				Thread.sleep(Core.getDelay());
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
			Log.d("Mirror", "mirror due to delay");
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
		Log.d("Mirror", "mirror");
		if (!isRegistered)
		{
			new RegisterTask(context).execute(new Void[]{});
			return;
		}
		long time = System.currentTimeMillis();
		if (!force)
		{
			long delay = Core.getDelay();
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
		Log.d("Mirror", "mirroring");
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		List<Map<String, List<Map<String, List<String>>>>> content = new LinkedList<>();
		String prevMode = "";
		String prevTable = "";
		int maxCount = Core.getMaxQueueCount();
		Map<String, List<Integer>> toBeRemoved = new HashMap<>();
		Cursor cursor = sqLiteDatabase.query("_queue", null, null, null, null, null, "timestamp ASC");
		for (int i=0; i<Math.min(maxCount, cursor.getCount()); i++)
		{
			cursor.moveToNext();
			String mode = cursor.getString(cursor.getColumnIndex("mode"));
			String table = cursor.getString(cursor.getColumnIndex("tabel"));
			String data = cursor.getString(cursor.getColumnIndex("data"));
			int id = cursor.getInt(cursor.getColumnIndex("id"));
			List<Integer> removeIDs;
			if (toBeRemoved.containsKey("table"))
			{
				removeIDs = toBeRemoved.get("table");
			}
			else
			{
				removeIDs = new LinkedList<>();
			}
			removeIDs.add(id);
			toBeRemoved.put(table, removeIDs);
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
		cursor.close();
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
				String host = Core.getHost();
				NetworkCommunication.send(json);
				for (String table : toBeRemoved.keySet())
				{
					List<Integer> ids = toBeRemoved.get(table);
					for (int id : ids)
					{
						sqLiteDatabase.delete("_queue", "id=? AND tabel=?", new String[]{id + "", table});
						ContentValues cv = new ContentValues();
						cv.put("_status", 1);
						sqLiteDatabase.update(table, cv, "_id=?", new String[]{id + ""});
						context.getContentResolver().notifyChange(Uri.parse(ContentProvider.CONTENT_URI + "/" + table + "/" + id), null);
					}
				}
			}
			catch (Exception e)
			{
				Log.e("Mirror", "error while mirroring", e);
			}
		}
	}
}
