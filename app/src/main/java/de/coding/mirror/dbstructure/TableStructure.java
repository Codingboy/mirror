package de.coding.mirror.dbstructure;

import java.util.HashSet;
import java.util.Set;

public class TableStructure
{
	public String name;
	public String mode;
	public Set<ColumnStructure> columns;

	public TableStructure()
	{
		columns = new HashSet<>();
		name = "";
		mode = "";
	}
}
