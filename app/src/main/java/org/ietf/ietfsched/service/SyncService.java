/*
 * Copyright 2011 Google Inc.
 * Copyright 2011 Isabelle Dalmasso.  
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ietf.ietfsched.service;

//import org.ietf.ietfsched.R;
import org.ietf.ietfsched.io.LocalExecutor;
import org.ietf.ietfsched.io.RemoteExecutor;
import org.ietf.ietfsched.provider.ScheduleProvider;
//import org.ietf.ietfsched.provider.ScheduleContract;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;

import android.app.IntentService;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
//import android.database.Cursor;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.format.DateUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

//import org.apache.http.client.HttpClient;

/**
 * Background {@link Service} that synchronizes data living in
 * {@link ScheduleProvider}. Reads data from both local {@link Resources} and
 * from remote sources, such as a spreadsheet.
 */
public class SyncService extends IntentService {
    private static final String TAG = "SyncService";
    private static final boolean debbug = false;

    public static final String EXTRA_STATUS_RECEIVER = "org.ietf.ietfsched.extra.STATUS_RECEIVER";

    public static final int STATUS_RUNNING = 0x1;
    public static final int STATUS_ERROR = 0x2;
    public static final int STATUS_FINISHED = 0x3;

    private static final int SECOND_IN_MILLIS = (int) DateUtils.SECOND_IN_MILLIS;

    /** Root worksheet feed for online data source */
	private static final String BASE_URL = "https://datatracker.ietf.org/meeting/107/";
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String ENCODING_GZIP = "gzip";

    private static final int VERSION_NONE = 0;
    private static final int VERSION_CURRENT = 47;

    private LocalExecutor mLocalExecutor;
    private RemoteExecutor mRemoteExecutor;

    public SyncService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        super.onCreate();
        final ContentResolver resolver = getContentResolver();
        mLocalExecutor = new LocalExecutor(getResources(), resolver);

        mRemoteExecutor = new RemoteExecutor();
		if (debbug) {
			Log.d(TAG, "SyncService OnCreate" + this.hashCode());
			String[] tz = TimeZone.getAvailableIDs();
			for (String id : tz) {
				Log.d(TAG, "Available timezone ids: " + id);
			}
		}
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_STATUS_RECEIVER);
        if (debbug) Log.d(TAG, "Receiver is = " + receiver);
        if (receiver != null) receiver.send(STATUS_RUNNING, Bundle.EMPTY);
        final Context context = this;
        final SharedPreferences prefs = getSharedPreferences(Prefs.IETFSCHED_SYNC, Context.MODE_PRIVATE);
        final int localVersion = prefs.getInt(Prefs.LOCAL_VERSION, VERSION_NONE);
		final String lastEtag = prefs.getString(Prefs.LAST_ETAG, "");

		Log.d(TAG, "found localVersion=" + localVersion + " and VERSION_CURRENT=" + VERSION_CURRENT);
		String remoteEtag = "";
	
		try {
			String htmlURL = BASE_URL + "agenda.csv";
			Log.d(TAG, 	"HEAD " + htmlURL);
			remoteEtag = mRemoteExecutor.executeHead(htmlURL);
			Log.d(TAG, 	"HEAD "  + htmlURL + " " + remoteEtag);
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		String csvURL = BASE_URL + "agenda.csv";
		try {
			if (debbug) Log.d(TAG, csvURL);
			InputStream agenda = mRemoteExecutor.executeGet(csvURL);
			mLocalExecutor.execute(agenda);
			Log.w("BeforeRemoveExec", "Before remote executor - inputsream achieved");
			prefs.edit().putString(Prefs.LAST_ETAG, remoteEtag).commit();
			prefs.edit().putInt(Prefs.LOCAL_VERSION, VERSION_CURRENT).commit();
			Log.d(TAG, "remote sync finished");
			if (receiver != null) receiver.send(STATUS_FINISHED, Bundle.EMPTY);
		}
		catch (Exception e) {
			Log.e(TAG, "Error HTTP request " + csvURL , e);
			final Bundle bundle = new Bundle();
			bundle.putString(Intent.EXTRA_TEXT, "iFOP Connection error. No updates.");
			if (receiver != null) {
				receiver.send(STATUS_ERROR, bundle);
			}
		}
    }


    private interface Prefs {
        String LAST_ETAG = "local_etag";
		String IETFSCHED_SYNC = "ietfsched_sync";
        String LOCAL_VERSION = "local_version";
		String LAST_LENGTH = "last_length";
		String LAST_SYNC_TIME = "last_stime";
    }
}
