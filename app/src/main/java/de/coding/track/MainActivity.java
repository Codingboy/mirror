package de.coding.track;

import android.app.Activity;
import android.content.ContentValues;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import de.coding.mirror.ContentProvider;
import de.coding.mirror.Core;
import de.coding.mirror.R;


public class MainActivity extends Activity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Log.d("Mirror", "--------------------------");
		setContentView(R.layout.activity_main);
		Core.init(getApplicationContext(), "http://192.168.2.105:8080/", R.array.DBStructure, 1000);
		ContentValues contentValues = new ContentValues();
		contentValues.put("_version", 0);
		getApplicationContext().getContentResolver().insert(Uri.parse(ContentProvider.CONTENT_URI + "/USERSW"), contentValues);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		Core.onStart(getApplicationContext());
		LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000 * 60 * 10, 100, new LocationListener(getApplicationContext()));
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 60 * 10, 100, new LocationListener(getApplicationContext()));
	}
}
