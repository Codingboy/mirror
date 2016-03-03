package de.coding.track;

import android.content.ContentValues;
import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;

import de.coding.mirror.ContentProvider;

public class LocationListener implements android.location.LocationListener
{
    Context context;

    public LocationListener(Context context)
    {
        this.context = context;
    }

    @Override
    public void onLocationChanged(Location location)
    {
        if (location != null)
        {
            float accuracy = location.getAccuracy();
            double altitude = location.getAltitude();
            float bearing = location.getBearing();
            float speed = location.getSpeed();
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            String provider = location.getProvider();
            ContentValues contentValues = new ContentValues();
            contentValues.put("ACCURACY", ""+accuracy);
            contentValues.put("ALTITUDE", ""+altitude);
            contentValues.put("BEARING", ""+bearing);
            contentValues.put("SPEED", ""+speed);
            contentValues.put("LATITUDE", ""+latitude);
            contentValues.put("LONGITUDE", ""+longitude);
			contentValues.put("PROVIDER", provider);
			contentValues.put("TIME", System.currentTimeMillis());
            context.getContentResolver().insert(Uri.parse(ContentProvider.CONTENT_URI + "/LOCATIONW"), contentValues);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {

    }

    @Override
    public void onProviderEnabled(String provider)
    {

    }

    @Override
    public void onProviderDisabled(String provider)
    {

    }
}
