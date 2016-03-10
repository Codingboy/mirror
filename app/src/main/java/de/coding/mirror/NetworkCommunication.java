package de.coding.mirror;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.UserDictionary;
import android.util.JsonWriter;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.coding.mirror.enums.ColumnKeys;
import de.coding.mirror.enums.JSONKeys;

public class NetworkCommunication
{
	private NetworkCommunication()
	{
	}

	/**
	 * Registers the framework at the server.
	 * @param context
	 * @return true if registered successful
	 * @note must not be called from ui thread
	 * @pre uuid needs to be set
	 * @pre pushyID needs to be set
	 * @pre host needs to be set
	 */
	protected static boolean register(Context context)
	{
		try
		{
			OutputStream out = new ByteArrayOutputStream();
			JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
			writer.beginObject();
			writer.name("uuid").value(Core.getUUID(context));
			writer.name("pushyID").value(Core.getPushyID());
			writer.endObject();
			writer.flush();
			writer.close();
			String json = out.toString();
			URL url = new URL(Core.getHost() + "register");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setConnectTimeout(0);
			connection.setDoInput(false);
			//connection.setRequestProperty("Accept", "application/json");
			OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
			Log.i("Mirror", "register: " + json);
			osw.write(json);
			osw.flush();
			osw.close();
			int response = connection.getResponseCode();
			//InputStream is = connection.getInputStream();
			//is.close();
			connection.disconnect();
			return response == 200;
		}
		catch (Exception e)
		{
			Log.e("Mirror", "error while register", e);
			return false;
		}
	}

	/**
	 * Sends data to the server.
	 * @param json content to send to the server
	 * @return http status code
	 * @throws IOException if error occured during transmission
	 * @pre host needs to be set
	 */
	protected static int send(String json) throws IOException
	{
		Log.i("Mirror", "send: " + json);
		URL url = new URL(Core.getHost()+"send");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/json");
		//connection.setRequestProperty("Accept", "application/json");
		connection.setConnectTimeout(0);
		connection.setDoInput(false);
		OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
		osw.write(json);
		osw.flush();
		osw.close();
		int response = connection.getResponseCode();
		connection.disconnect();
		return response;
	}

	/**
	 * Handles the receiving of data from the server
	 * @param context application context
	 * @throws IOException if error occured during transmission
	 * @throws JSONException if server delivered invalid json
	 * @pre host needs to be set
	 * @pre uuid needs to be set
	 */
	protected static void receive(Context context) throws IOException, JSONException
	{
		URL url = new URL(Core.getHost()+"receive");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setRequestProperty("Accept", "application/json");
		connection.setConnectTimeout(0);
		connection.setDoInput(true);
		OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
		OutputStream out = new ByteArrayOutputStream();
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
		writer.beginObject();
		writer.name("uuid").value(Core.getUUID(context));
		writer.endObject();
		writer.flush();
		writer.close();
		String json = out.toString();
		Log.i("Mirror", "receive: " + json);
		osw.write(json);
		osw.flush();
		osw.close();
		InputStream is = connection.getInputStream();
		BufferedReader streamReader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		StringBuilder stringBuilder = new StringBuilder();
		String inputStr;
		while ((inputStr = streamReader.readLine()) != null)
		{
			stringBuilder.append(inputStr);
		}
		is.close();
		connection.disconnect();

		json = stringBuilder.toString();
		Log.i("Mirror", json);
		JSONObject jsonObject = new JSONObject(json);
		JSONArray content = jsonObject.getJSONArray(JSONKeys.content.toString());
		HashMap<String, HashMap<Integer, Integer>> ackMap = new HashMap<>();
		for (int i=0; i<content.length(); i++)
		{
			JSONObject jsonObject1 = content.getJSONObject(i);
			boolean delete = false;
			Iterator<String> iterator = jsonObject1.keys();
			JSONArray jsonArray = null;
			while (iterator.hasNext())
			{
				String mode = iterator.next();
				if (mode.equals(JSONKeys.delete.toString()))
				{
					delete = true;
				}
				jsonArray = jsonObject1.getJSONArray(mode);
			}
			for (int j=0; j<jsonArray.length(); j++)
			{
				JSONObject jsonObject2 = jsonArray.getJSONObject(j);
				Iterator<String> iterator1 = jsonObject2.keys();
				while (iterator1.hasNext())
				{
					String table = iterator1.next();
					HashMap<Integer, Integer> ackTable;
					if (ackMap.containsKey(table))
					{
						ackTable = ackMap.get(table);
					}
					else
					{
						ackTable = new HashMap<Integer, Integer>();
					}
					JSONArray jsonArray1 = jsonObject2.getJSONArray(table);
					for (int k=0; k<jsonArray1.length(); k++)
					{
						JSONObject jsonObject3 = jsonArray1.getJSONObject(k);
						Iterator<String> iterator2 = jsonObject3.keys();
						ContentValues contentValues = new ContentValues();
						while (iterator2.hasNext())
						{
							String key = iterator2.next();
							Object value = jsonObject3.get(key);
							if (value instanceof String)
							{
								contentValues.put(key, (String)value);
							}
							else if (value instanceof Integer)
							{
								contentValues.put(key, (Integer)value);
							}
						}
						ackTable.put(contentValues.getAsInteger(ColumnKeys._id.toString()), contentValues.getAsInteger(ColumnKeys._version.toString()));
						if (delete)
						{
							context.getContentResolver().delete(Uri.parse(UserDictionary.Words.CONTENT_URI + "/" + table), ColumnKeys._id.toString()+"=?", new String[]{contentValues.getAsString(ColumnKeys._id.toString())});
						}
						else
						{
							context.getContentResolver().insert(Uri.parse(UserDictionary.Words.CONTENT_URI + "/" + table), contentValues);
						}
					}
					ackMap.put(table, ackTable);
				}
			}
		}
		if (!ackMap.isEmpty())
		{
			ack(ackMap, context);
		}
	}

	/**
	 * Acknowledges the received data.
	 * @param context application context
	 * @param ackMap {TableName:{_id:_version}}
	 * @throws IOException if error occured during transmission
	 * @pre host needs to be set
	 * @pre uuid needs to be set
	 */
	private static void ack(Map<String, HashMap<Integer, Integer>> ackMap, Context context) throws IOException
	{
		OutputStream out = new ByteArrayOutputStream();
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
		writer.beginObject();
		writer.name(JSONKeys.uuid.toString()).value(Core.getUUID(context));
		writer.name(JSONKeys.content.toString());
		writer.beginArray();
		for (String table : ackMap.keySet())
		{
			writer.beginObject();
			writer.name(table);
			writer.beginArray();
			HashMap<Integer, Integer> ackTable = ackMap.get(table);
			for (Integer key : ackTable.keySet())
			{
				writer.beginObject();
				writer.name(ColumnKeys._id.toString()).value(key);
				writer.name(ColumnKeys._version.toString()).value(ackTable.get(key));
				writer.endObject();
			}
			writer.endArray();
			writer.endObject();
		}
		writer.endArray();
		writer.endObject();
		writer.flush();
		writer.close();
		String json = out.toString();
		URL url = new URL(Core.getHost()+"ack");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/json");
		//connection.setRequestProperty("Accept", "application/json");
		connection.setConnectTimeout(0);
		connection.setDoInput(false);
		OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
		Log.i("Mirror", "ack: " + json);
		osw.write(json);
		osw.flush();
		osw.close();
		int response = connection.getResponseCode();
		//InputStream is = connection.getInputStream();
		//is.close();
		connection.disconnect();
	}
}
