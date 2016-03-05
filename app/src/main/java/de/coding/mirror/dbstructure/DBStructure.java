package de.coding.mirror.dbstructure;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

public class DBStructure
{
	public Map<String, TableStructure> tables;
	private static DBStructure instance;

	private DBStructure()
	{
		tables = new HashMap<>();
	}

	public TableStructure getTableStructure(String name)
	{
		if (tables.containsKey(name))
		{
			return tables.get(name);
		}
		TableStructure tableStructure = new TableStructure();
		tableStructure.name = name;
		tables.put(name, tableStructure);
		return tableStructure;
	}

	public static DBStructure getInstance(Context context, int dbStructure)
	{
		if (instance != null)
		{
			return instance;
		}
		instance = new DBStructure();
		String[] strings = context.getResources().getStringArray(dbStructure);
		for (String str : strings)
		{
			String[] arr = str.split("_");
			String table = arr[0];
			TableStructure tableStructure = instance.getTableStructure(table);
			if (arr.length == 2)
			{
				String mode = arr[1];
				tableStructure.mode = mode;
			}
			if (arr.length == 3)
			{
				String columnName = arr[1];
				String columnType = arr[2];
				ColumnStructure columnStructure = new ColumnStructure();
				columnStructure.type = columnType;
				columnStructure.name = columnName;
				tableStructure.columns.put(columnName, columnStructure);
			}
		}
		for (String key : instance.tables.keySet())
		{
			TableStructure tableStructure = instance.tables.get(key);
			if (tableStructure.mode.equals("CS"))
			{
				ColumnStructure columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_id";
				tableStructure.columns.put(columnStructure.name, columnStructure);
				columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_version";
				tableStructure.columns.put(columnStructure.name, columnStructure);
				columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_status";
				tableStructure.columns.put(columnStructure.name, columnStructure);
			}
			else if (tableStructure.mode.equals("SC"))
			{
				ColumnStructure columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_id";
				tableStructure.columns.put(columnStructure.name, columnStructure);
				columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_version";
				tableStructure.columns.put(columnStructure.name, columnStructure);
			}
			else if (tableStructure.mode.equals("SL"))
			{
				ColumnStructure columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_id";
				tableStructure.columns.put(columnStructure.name, columnStructure);
			}
			else if (tableStructure.mode.equals("CL"))
			{
				ColumnStructure columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_id";
				tableStructure.columns.put(columnStructure.name, columnStructure);
			}
		}
		return instance;
	}
}
