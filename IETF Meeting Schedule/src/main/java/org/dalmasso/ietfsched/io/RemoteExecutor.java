/*
 * Copyright 2011 Google Inc.
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

package org.dalmasso.ietfsched.io;

//import com.google.android.apps.iosched.io.XmlHandler.HandlerException;
//import com.google.android.apps.iosched.util.ParserUtils;

import android.util.Log;

import org.apache.http.cookie.Cookie;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
//import org.xmlpull.v1.XmlPullParser;
//import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentResolver;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes an {@link HttpUriRequest} and passes the result as an
 * {@link XmlPullParser} to the given {@link XmlHandler}.
 */
public class RemoteExecutor {

	private static final String TAG = "RemoteExecutor HTTP";

    private final HttpClient mHttpClient;

    public RemoteExecutor(HttpClient httpClient) {
        mHttpClient = httpClient;
    }


	public String executeHead(String url) throws Exception {
		final HttpUriRequest request = new HttpHead(url);
		final HttpResponse resp = mHttpClient.execute(request);
		final int status = resp.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_OK) {
			Log.d(TAG, "Response Code " + status);
            throw new IOException("Unexpected server response " + resp.getStatusLine() + " for " + request.getRequestLine());
		}
//		Header h = resp.getFirstHeader("Content-Length");
		Header h = resp.getFirstHeader("Etag");
		if (h != null) {
			try {
//				int length = Integer.parseInt(h.getValue());
//				Log.d(TAG, "Content-Length " + length);
				String etag = h.getValue();
				Log.d(TAG, "Etag " + h.getValue());
				return etag;
			}
			catch (Exception e) {
				e.printStackTrace();
				return null;
			}	
		}
		return null;
	}

    /**
     * Execute a {@link HttpGet} request, passing a valid response through
     * {@link XmlHandler#parseAndApply(XmlPullParser, ContentResolver)}.
     */
    public InputStream executeGet(String url) throws Exception {
		// Last-Modified Thu, 01 Sep 2011 16:11:33 GMT
		//ETag "aa4a6d-41d1-4abe37f915740"-gzip
	
        HttpUriRequest request = new HttpGet(url);
		HttpResponse resp = mHttpClient.execute(request);
        int status = resp.getStatusLine().getStatusCode();
			
        if (status != HttpStatus.SC_OK) {
			Log.d(TAG, "Response Code " + status);
			throw new IOException("Unexpected server response " + resp.getStatusLine() + " for " + request.getRequestLine());
		}
		return resp.getEntity().getContent();
    }
}
