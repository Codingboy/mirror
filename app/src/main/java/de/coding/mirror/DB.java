package de.coding.mirror;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
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
import de.coding.mirror.enums.ColumnKeys;
import de.coding.mirror.enums.JSONKeys;
import de.coding.mirror.enums.QueueKeys;
import me.pushy.sdk.Pushy;
import me.pushy.sdk.exceptions.PushyException;

public class DB extends SQLiteOpenHelper
{
	private Context context;
	private static DB instance;
	private long lastTimeMirrored;
	private boolean isDelayed;
	private boolean isRegistered;

	/**
	 * Gets an instance.
	 * @param context context
	 * @param dbStructure DBStructure.xml as integer
	 * @return instance
	 */
	protected static DB getInstance(Context context, int dbStructure)
	{
		if (instance != null)
		{
			return instance;
		}
		instance = new DB(context);
		return instance;
	}

	/**
	 * Initialises the object.
	 * @param context context
	 */
	private DB(Context context)
	{
		super(context, "Mirror", null, 1);
		this.context = context;
		lastTimeMirrored = -1;
		isDelayed = false;
		SharedPreferences sharedPreferences = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
		if (sharedPreferences.contains("registered"))
		{
			isRegistered = sharedPreferences.getBoolean("registered", false);
		}
		else
		{
			isRegistered = false;
		}
	}

	@Override
	public void onCreate(SQLiteDatabase sqLiteDatabase)
	{
		DBStructure dbStructure = DBStructure.getInstance(context, Core.getDBStructureNumber());
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
			stringBuilder.append(", PRIMARY KEY("+ColumnKeys._id.toString()+"))");
			String sql = stringBuilder.toString();
			Log.i("Mirror", sql);
			sqLiteDatabase.execSQL(sql);
		}
		String sql = "CREATE TABLE IF NOT EXISTS "+QueueKeys._queue.toString()+" ("+QueueKeys.id.toString()+" INTEGER, "+QueueKeys.mode.toString()+" TEXT, "+QueueKeys.tabel.toString()+" TEXT, "+QueueKeys.data.toString()+" TEXT, "+QueueKeys.timestamp.toString()+" INTEGER, PRIMARY KEY("+QueueKeys.id.toString()+", "+QueueKeys.tabel.toString()+"))";
		Log.i("Mirror", sql);
		sqLiteDatabase.execSQL(sql);
	}

	@Override
	public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2)
	{
		reset(sqLiteDatabase);
	}

	/**
	 * Resets the database.
	 * @param sqLiteDatabase sqLiteDatabase
	 */
	protected void reset(SQLiteDatabase sqLiteDatabase)
	{
		DBStructure dbStructure = DBStructure.getInstance(context, Core.getDBStructureNumber());
		for (String key : dbStructure.tables.keySet())
		{
			TableStructure tableStructure = dbStructure.tables.get(key);
			String sql = "DROP TABLE "+tableStructure.name;
			Log.i("Mirror", sql);
			sqLiteDatabase.execSQL(sql);
		}
		String sql = "DROP TABLE "+QueueKeys._queue.toString();
		Log.i("Mirror", sql);
		sqLiteDatabase.execSQL(sql);
		onCreate(sqLiteDatabase);
	}

	/**
	 * Resets the database.
	 */
	public void reset()
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		reset(sqLiteDatabase);
	}

	/**
	 * Deletes a row from the table.
	 * @param table tableName
	 * @param where where
	 * @param whereArgs whereArgs
	 * @param context context
	 * @return number of deleted rows
	 */
	protected int delete(String table, String where, String[] whereArgs, Context context)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		int count = sqLiteDatabase.delete(table, where, whereArgs);
		DBStructure dbStructure = DBStructure.getInstance(context, Core.getDBStructureNumber());
		String mode = dbStructure.tables.get(table).mode;
		if (mode.equals("CS"))
		{
			writeToQueue(Integer.valueOf(whereArgs[0]), "delete", table, context);
		}
		return count;
	}

	/**
	 * Inserts a row into a table. Updates on conflict.
	 * @param table tableName
	 * @param contentValues contentValues
	 * @param context context
	 * @return _id of the inserted row
	 */
	protected long insert(String table, ContentValues contentValues, Context context)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		DBStructure dbStructure = DBStructure.getInstance(context, Core.getDBStructureNumber());
		String mode = dbStructure.tables.get(table).mode;
		if (mode.equals("CS"))
		{
			int version = 0;
			if (contentValues.containsKey(ColumnKeys._version.toString()))
			{
				version = contentValues.getAsInteger(ColumnKeys._version.toString());
			}
			else
			{
				if (contentValues.containsKey(ColumnKeys._id.toString()))
				{
					Cursor c = sqLiteDatabase.query(table, new String[]{ColumnKeys._version.toString()}, ColumnKeys._id.toString()+"=?", new String[]{contentValues.getAsInteger(ColumnKeys._id.toString()) + ""}, null, null, null);
					if (c.getCount() > 0)
					{
						c.moveToFirst();
						version = c.getInt(0);
						version++;
					}
					c.close();
				}
			}
			contentValues.put(ColumnKeys._version.toString(), version);
			contentValues.put(ColumnKeys._status.toString(), 0);
		}
		long id = sqLiteDatabase.insertWithOnConflict(table, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
		if (mode.equals("CS"))
		{
			writeToQueue(id, "insert", table, context);
		}
		return id;
	}

	/**
	 * Enqueues a row to mirror it to the server.
	 * @param id _id of a row
	 * @param mode "insert" or "delete"
	 * @param table tableName
	 * @param context context
	 */
	private void writeToQueue(long id, String mode, String table, Context context)
	{
		//TODO merge
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("{");
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		Cursor cursor = sqLiteDatabase.query(table, null, ColumnKeys._id.toString()+"=?", new String[]{id+""}, null, null, null);
		cursor.moveToFirst();
		for (int i=0; i<cursor.getCount(); i++)
		{
			int statusFix = 0;
			for (int j=0; j<cursor.getColumnCount(); j++)
			{
				if (cursor.getColumnName(j).equals(ColumnKeys._status.toString()))
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
		cv.put(QueueKeys.id.toString(), id);
		cv.put(QueueKeys.mode.toString(), mode);
		cv.put(QueueKeys.tabel.toString(), table);
		cv.put(QueueKeys.data.toString(), json);
		cv.put(QueueKeys.timestamp.toString(), System.currentTimeMillis());
		sqLiteDatabase.insertWithOnConflict(QueueKeys._queue.toString(), null, cv, SQLiteDatabase.CONFLICT_REPLACE);
		Log.d("Mirror", "mirroring due to db interaction");
		new MirrorTask(context).execute(new Void[]{});
	}

	/**
	 * Mirrors the database changes to server.
	 * @pre host needs to be set
	 * @pre uuid needs to be set
	 * @pre delay needs to be set
	 * @post pushyID is initialised
	 */
	private class MirrorTask extends AsyncTask<Void, Void, Void>
	{
		Context context;

		protected MirrorTask(Context context)
		{
			this.context = context;
		}

		private MirrorTask()
		{
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
		protected void onPreExecute()
		{
		}

		@Override
		protected void onProgressUpdate(Void... values)
		{
		}
	}

	/**
	 * Registers at the server.
	 * @pre host needs to be set
	 * @pre uuid needs to be set
	 * @pre delay needs to be set
	 * @post pushyID is initialised
	 */
	private class RegisterTask extends AsyncTask<Void, Void, Void>
	{
		Context context;

		protected RegisterTask(Context context)
		{
			this.context = context;
		}

		private RegisterTask()
		{
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
				SharedPreferences sharedPreferences = context.getSharedPreferences("Mirror", Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = sharedPreferences.edit();
				editor.putBoolean("registered", isRegistered);
				editor.apply();
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
		protected void onPreExecute()
		{
		}

		@Override
		protected void onProgressUpdate(Void... values)
		{
		}
	}

	/**
	 * Updates a table. Inserts on conflict.
	 * @param table tableName
	 * @param contentValues new values
	 * @param where where
	 * @param whereArgs whereArgs
	 * @param context context
	 * @return number of affected rows
	 */
	protected int update(String table, ContentValues contentValues, String where, String[] whereArgs, Context context)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		DBStructure dbStructure = DBStructure.getInstance(context, Core.getDBStructureNumber());
		String mode = dbStructure.tables.get(table).mode;
		if (mode.equals("CS"))
		{
			int version = 0;
			if (contentValues.containsKey(ColumnKeys._version.toString()))
			{
				version = contentValues.getAsInteger(ColumnKeys._version.toString());
			}
			else
			{
				if (contentValues.containsKey(ColumnKeys._id.toString()))
				{
					Cursor c = sqLiteDatabase.query(table, new String[]{ColumnKeys._version.toString()}, ColumnKeys._id.toString()+"=?", new String[]{contentValues.getAsInteger(ColumnKeys._id.toString()) + ""}, null, null, null);
					if (c.getCount() > 0)
					{
						c.moveToFirst();
						version = c.getInt(0);
						version++;
					}
					c.close();
				}
			}
			contentValues.put(ColumnKeys._version.toString(), version);
			contentValues.put(ColumnKeys._status.toString(), 0);
		}
		int count = sqLiteDatabase.updateWithOnConflict(table, contentValues, where, whereArgs, SQLiteDatabase.CONFLICT_REPLACE);
		if (mode.equals("CS"))
		{
			writeToQueue(contentValues.getAsInteger(ColumnKeys._id.toString()), "update", table, context);
		}
		return count;
	}

	/**
	 * Query the given table.
	 * @param table name of table
	 * @param projection columnNames
	 * @param selection select
	 * @param selectionArgs selectArguments
	 * @param sortOrder orderBy
	 * @return cursor
	 */
	protected Cursor query(String table, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		SQLiteDatabase sqLiteDatabase = getWritableDatabase();
		return sqLiteDatabase.query(table, projection, selection, selectionArgs, null, null, sortOrder);
	}

	/**
	 * Mirrors the database changes to server after a delay.
	 * @pre host needs to be set
	 * @pre uuid needs to be set
	 * @pre delay needs to be set
	 * @post pushyID is initialised
	 */
	private class MirrorCallback extends AsyncTask<Void, Void, Void>
	{
		Context context;

		protected MirrorCallback(Context context)
		{
			this.context = context;
		}

		private MirrorCallback()
		{
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
		protected void onPreExecute()
		{
		}

		@Override
		protected void onProgressUpdate(Void... values)
		{
		}
	}

	/**
	 * Mirrors the database changes to server.
	 * @param context context
	 * @param force true to force an update independent if there was a recent update
	 * @pre host needs to be set
	 * @pre uuid needs to be set
	 * @post pushyID is initialised
	 */
	protected void mirror(Context context, boolean force)
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
		Cursor cursor = sqLiteDatabase.query(QueueKeys._queue.toString(), null, null, null, null, null, "timestamp ASC");
		for (int i=0; i<Math.min(maxCount, cursor.getCount()); i++)
		{
			cursor.moveToNext();
			String mode = Core.getString(cursor, QueueKeys.mode.toString());
			String table = Core.getString(cursor, QueueKeys.tabel.toString());
			String data = Core.getString(cursor, QueueKeys.data.toString());
			int id = Core.getInt(cursor, QueueKeys.id.toString());
			List<Integer> removeIDs;
			if (toBeRemoved.containsKey(table))
			{
				removeIDs = toBeRemoved.get(table);
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
				writer.name(JSONKeys.uuid.toString()).value(Core.getUUID(context));
				writer.name(JSONKeys.content.toString());
				writer.beginArray();
				for (Map<String, List<Map<String, List<String>>>> map : content)
				{
					writer.beginObject();
					List<Map<String, List<String>>> list;
					if (map.containsKey(JSONKeys.insert.toString()))
					{
						writer.name(JSONKeys.insert.toString());
						list = map.get(JSONKeys.insert.toString());
					}
					else
					{
						writer.name(JSONKeys.delete.toString());
						list = map.get(JSONKeys.delete.toString());
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
				NetworkCommunication.send(json);
				for (String table : toBeRemoved.keySet())
				{
					List<Integer> ids = toBeRemoved.get(table);
					for (int id : ids)
					{
						sqLiteDatabase.delete(QueueKeys._queue.toString(), QueueKeys.id.toString()+"=? AND "+QueueKeys.tabel.toString()+"=?", new String[]{id + "", table});
						ContentValues cv = new ContentValues();
						cv.put(ColumnKeys._status.toString(), 1);
						sqLiteDatabase.update(table, cv, ColumnKeys._id.toString()+"=?", new String[]{id + ""});
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
