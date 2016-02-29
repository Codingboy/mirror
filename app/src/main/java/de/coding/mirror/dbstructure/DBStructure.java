package de.coding.mirror.dbstructure;

import android.content.Context;

import java.util.HashSet;
import java.util.Set;

public class DBStructure
{
	public Set<TableStructure> tables;
	private static DBStructure instance;

	private DBStructure()
	{
		tables = new HashSet<>();
	}

	public TableStructure getTableStructure(String name)
	{
		for (TableStructure tableStructure : tables)
		{
			if (tableStructure.name.equals(name))
			{
				return tableStructure;
			}
		}
		TableStructure tableStructure = new TableStructure();
		tableStructure.name = name;
		tables.add(tableStructure);
		return tableStructure;
	}

	public String getMode(String table)
	{
		for (TableStructure tableStructure : tables)
		{
			if (tableStructure.name.equals(table))
			{
				return tableStructure.mode;
			}
		}
		return "";
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
				tableStructure.columns.add(columnStructure);
			}
		}
		for (TableStructure tableStructure : instance.tables)
		{
			if (tableStructure.mode.equals("CS"))
			{
				ColumnStructure columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_id";
				tableStructure.columns.add(columnStructure);
				columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_version";
				tableStructure.columns.add(columnStructure);
				columnStructure.type = "INTEGER";
				columnStructure.name = "_status";
				tableStructure.columns.add(columnStructure);
			}
			if (tableStructure.mode.equals("SC"))
			{
				ColumnStructure columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_id";
				tableStructure.columns.add(columnStructure);
				columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_version";
				tableStructure.columns.add(columnStructure);
			}
			if (tableStructure.mode.equals("L"))
			{
				ColumnStructure columnStructure = new ColumnStructure();
				columnStructure.type = "INTEGER";
				columnStructure.name = "_id";
				tableStructure.columns.add(columnStructure);
			}
		}
		return instance;
	}
}
