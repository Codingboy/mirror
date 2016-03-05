package de.coding.mirror.dbstructure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TableStructure
{
	public String name;
	public String mode;
	public Map<String, ColumnStructure> columns;

	public TableStructure()
	{
		columns = new HashMap<>();
		name = "";
		mode = "";
	}
}
