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

package org.dalmasso.ietfsched.service;

//import org.dalmasso.ietfsched.R;
import org.dalmasso.ietfsched.io.LocalExecutor;
import org.dalmasso.ietfsched.io.RemoteExecutor;
import org.dalmasso.ietfsched.provider.ScheduleProvider;
//import org.dalmasso.ietfsched.provider.ScheduleContract;
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

//import org.apache.http.client.HttpClient;

/**
 * Background {@link Service} that synchronizes data living in
 * {@link ScheduleProvider}. Reads data from both local {@link Resources} and
 * from remote sources, such as a spreadsheet.
 */
public class SyncService extends IntentService {
    private static final String TAG = "SyncService";
    private static final boolean debbug = true;

    public static final String EXTRA_STATUS_RECEIVER = "org.dalmasso.ietfsched.extra.STATUS_RECEIVER";

    public static final int STATUS_RUNNING = 0x1;
    public static final int STATUS_ERROR = 0x2;
    public static final int STATUS_FINISHED = 0x3;

    private static final int SECOND_IN_MILLIS = (int) DateUtils.SECOND_IN_MILLIS;

    /** Root worksheet feed for online data source */
    // TODO: insert your sessions/speakers/vendors spreadsheet doc URL here.
    // private static final String WORKSHEETS_URL = "INSERT_SPREADSHEET_URL_HERE";
	private static final String BASE_URL = "https://datatracker.ietf.org/meeting/99/";
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
        final ContentResolver resolver = getContentResolver();
        mLocalExecutor = new LocalExecutor(getResources(), resolver);
	
        final HttpClient httpClient = getHttpClient(this);	
		mRemoteExecutor = new RemoteExecutor(httpClient);
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
//		final int lastLength = prefs.getInt(Prefs.LAST_LENGTH, VERSION_NONE);
		final String lastEtag = prefs.getString(Prefs.LAST_ETAG, "");
//		final long startLocal = System.currentTimeMillis();
	
		//boolean localParse = localVersion < VERSION_CURRENT;
		boolean localParse = false;
		Log.d(TAG, "found localVersion=" + localVersion + " and VERSION_CURRENT=" + VERSION_CURRENT);
		boolean remoteParse;
//		int remoteLength = -1;
		String remoteEtag = "";
	
		try {
			String htmlURL = BASE_URL + "agenda.csv";
			if (debbug) Log.d(TAG, 	"HEAD " + htmlURL);
			remoteEtag = mRemoteExecutor.executeHead(htmlURL);
			if (debbug) Log.d(TAG, 	"HEAD "  + htmlURL + " " + remoteEtag);
			if (remoteEtag == null) {
				Log.d(TAG, "Error connection, cannot retrieve any information from" + htmlURL);
				remoteParse = false;
			}
			else {
				remoteParse = !remoteEtag.equals(lastEtag);
				}
		}
		catch (Exception e) {
			remoteParse = false;
			e.printStackTrace();
		}

// HACK FOR TESTS PURPOSES. TO REMOVE 
//		Log.w(TAG, "For tests purposes, only the local parsing is activated");
//		remoteParse = false;
//		localParse = true;
// HACK FIN.	
	
		if (!remoteParse && !localParse) {
			Log.d(TAG, "Already synchronized");
			if (receiver != null) receiver.send(STATUS_FINISHED, Bundle.EMPTY);
			return;
			}
	
		if (remoteParse) {
			String csvURL = BASE_URL + "agenda.csv";
			try {
				if (debbug) Log.d(TAG, csvURL);
				InputStream agenda = mRemoteExecutor.executeGet(csvURL);
				mLocalExecutor.execute(agenda);
				prefs.edit().putString(Prefs.LAST_ETAG, remoteEtag).commit();
				prefs.edit().putInt(Prefs.LOCAL_VERSION, VERSION_CURRENT).commit();
				localParse = false;
				Log.d(TAG, "remote sync finished");
				if (receiver != null) receiver.send(STATUS_FINISHED, Bundle.EMPTY);
			}
			catch (Exception e) {
				Log.e(TAG, "Error HTTP request " + csvURL , e);
				if (!localParse) {
					final Bundle bundle = new Bundle();
					bundle.putString(Intent.EXTRA_TEXT, "Connection error. No updates.");
					if (receiver != null) {
						receiver.send(STATUS_ERROR, bundle);
					}
				}
			}
		}
		
		if (localParse) {
			try {
				mLocalExecutor.execute(context, "agenda-83.csv");
				Log.d(TAG, "local sync finished");
				prefs.edit().putInt(Prefs.LOCAL_VERSION, VERSION_CURRENT).commit();
				if (receiver != null) receiver.send(STATUS_FINISHED, Bundle.EMPTY);
			}
			catch (Exception e) {
				e.printStackTrace();
				final Bundle bundle = new Bundle();
				bundle.putString(Intent.EXTRA_TEXT, e.toString());
				if (receiver != null) {
					receiver.send(STATUS_ERROR, bundle);
				}
			}
		}

/*		Log.d(TAG, "Check DB");
		try {
			android.net.Uri contentURI = android.net.Uri.parse("content://org.dalmasso.ietfsched/sessions_tracks");
			final ContentResolver resolver = getContentResolver();
			Cursor cursor = resolver.query(contentURI, null, null, null, null);
			int columns = cursor.getColumnCount();
		
			while (cursor.moveToNext()) {
				Log.d(TAG, "Cursor position " + cursor.getPosition() + " " + cursor.getString(1) + "/" + cursor.getString(2));
				}
			}
		catch (Exception e) {
			e.printStackTrace();
		}
*/	
	
	
    }


   /**
     * Generate and return a {@link HttpClient} configured for general use,
     * including setting an application-specific user-agent string.
     */
	public static HttpClient getHttpClient(Context context) {
        final HttpParams params = new BasicHttpParams();

        // Use generous timeouts for slow mobile networks
        HttpConnectionParams.setConnectionTimeout(params, 20 * SECOND_IN_MILLIS);
        HttpConnectionParams.setSoTimeout(params, 20 * SECOND_IN_MILLIS);

        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpProtocolParams.setUserAgent(params, buildUserAgent(context));

        final DefaultHttpClient client = new DefaultHttpClient(params);

        client.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(HttpRequest request, HttpContext context) {
                // Add header to accept gzip content
                if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
                    request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
                }
/*				Log.d(TAG, "Headers for request");
				Header[] headers = request.getAllHeaders();
				for (Header h : headers) {
					Log.d(TAG, h.getName() + " " + h.getValue());
				}
*/			

            }
        });

        client.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(HttpResponse response, HttpContext context) {
                // Inflate any responses compressed with gzip
			
/*				Log.d(TAG, "Headers for response");
				Header[] headers = response.getAllHeaders();
				for (Header h : headers) {
					Log.d(TAG, h.getName() + " " + h.getValue());
				}
*/			

                final HttpEntity entity = response.getEntity();
                final Header encoding = entity != null ? entity.getContentEncoding() : null;
                if (encoding != null) {
                    for (HeaderElement element : encoding.getElements()) {
                        if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
                            response.setEntity(new InflatingEntity(response.getEntity()));
                            break;
                        }
                    }
                }
            }
        });

        return client;
	}
	
	
  /**
     * Build and return a user-agent string that can identify this application
     * to remote servers. Contains the package name and version code.
     */
    private static String buildUserAgent(Context context) {
        try {
            final PackageManager manager = context.getPackageManager();
            final PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);

            // Some APIs require "(gzip)" in the user-agent string.
            return info.packageName + "/" + info.versionName
                    + " (" + info.versionCode + ") (gzip)";
        }
	catch (NameNotFoundException e) {
            return null;
        }
    }

   /**
     * Simple {@link HttpEntityWrapper} that inflates the wrapped
     * {@link HttpEntity} by passing it through {@link GZIPInputStream}.
     */
    private static class InflatingEntity extends HttpEntityWrapper {
        public InflatingEntity(HttpEntity wrapped) {
            super(wrapped);
        }

        @Override
        public InputStream getContent() throws IOException {
            return new GZIPInputStream(wrappedEntity.getContent());
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }

	


    private interface Prefs {
        String LAST_ETAG = "local_etag";
		String IETFSCHED_SYNC = "ietfsched_sync";
        String LOCAL_VERSION = "local_version";
		String LAST_LENGTH = "last_length";
		//String LAST_SYNC_TIME = "last_stime";
    }
}
