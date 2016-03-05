package de.coding.track;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
		Core.init(getApplicationContext(), "http://192.168.2.105:8080/", R.array.DBStructure, 1000, 50);
		LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000 * 60 * 10, 100, new LocationListener(getApplicationContext()));
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 60 * 10, 100, new LocationListener(getApplicationContext()));
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
		{
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Please enable GPS.")
					.setPositiveButton("OK",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface d, int id) {
									startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
									d.dismiss();
								}
							})
					.setNegativeButton("Cancel",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface d, int id) {
									d.cancel();
								}
							});
			builder.create().show();
		}
	}

	@Override
	protected void onStart()
	{
		super.onStart();
	}
}
