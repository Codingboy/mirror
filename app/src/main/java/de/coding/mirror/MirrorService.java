package de.coding.mirror;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

import de.coding.mirror.dbstructure.DBStructure;

/*
TODO delete calss
TODO add onSQL mirror
 */
public class MirrorService extends Service
{
	public static final int SLEEPTIME = 1000;

	@Override
	public void onCreate()
	{
		super.onCreate();
		Runnable runnable = new Runnable()
		{
			@Override
			public void run()
			{
				long last = System.currentTimeMillis();
				int dbStructure = Core.getDBStructureNumber();
				while (true)
				{
					try
					{
						DB db = DB.getInstance(getApplicationContext(), dbStructure);
						Log.d("Mirror", "mirroring due to mirrorservice");
						db.mirror(getApplicationContext(), false);
					}
					catch (Exception e)
					{
						Log.e("Mirror", "error while trying to mirror database", e);
					}
					try
					{
						long now = System.currentTimeMillis();
						long diff = now - last;
						long sleep = SLEEPTIME - diff;
						Log.d("Mirror", sleep+"");
						if (sleep < 0)
						{
							sleep = 0;
						}
						last = now;
						Thread.sleep(sleep);
					}
					catch (InterruptedException e)
					{
						Log.e("Mirror", "error during sleep", e);
					}
				}
			}
		};
		Thread thread = new Thread(runnable);
		thread.start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
