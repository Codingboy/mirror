package de.coding.mirror;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

public class PushReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
		ReceiveTask receiveTask = new ReceiveTask(context);
		receiveTask.execute(new Void[]{});
    }

	private static class ReceiveTask extends AsyncTask<Void, Void, Void>
	{
		Context context;

		ReceiveTask(Context context)
		{
			this.context = context;
		}

		@Override
		protected Void doInBackground(Void... params)
		{
			try
			{
				NetworkCommunication.receive(context);
				Core.notify(context, "Mirror", "new Content received", null);
			}
			catch (Exception e)
			{
				Log.e("Mirror", "error during receiving data", e);
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
}