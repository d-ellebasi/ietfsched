/*
 * Copyright 2001 Isabelle Dalmasso.
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

package org.ietf.ietfsched.io;

import org.ietf.ietfsched.util.ParserUtils;
import org.ietf.ietfsched.provider.ScheduleContract;
import org.ietf.ietfsched.provider.ScheduleContract.Blocks;
import org.ietf.ietfsched.provider.ScheduleContract.Rooms;
import org.ietf.ietfsched.provider.ScheduleContract.Sessions;
import org.ietf.ietfsched.provider.ScheduleContract.Tracks;
import org.ietf.ietfsched.provider.ScheduleDatabase.SessionsTracks;
import org.ietf.ietfsched.util.Lists;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
//import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.content.Context;
import android.content.res.Resources;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Opens a local {@link Resources#getXml(int)} and passes the resulting
 * {@link XmlPullParser} to the given {@link XmlHandler}.
 */
public class LocalExecutor {
	private static final String TAG = "LocalExecutor";
	private static final boolean debbug = false;
    private Resources mRes;
    private ContentResolver mResolver;
	private final String mAuthority = ScheduleContract.CONTENT_AUTHORITY;
	private final HashSet<String> blockRefs = new HashSet<String>();


    public LocalExecutor(Resources res, ContentResolver resolver) {
        mRes = res;
        mResolver = resolver;
    }

	public void execute(Context context, String assetName) {
		try {
			Log.d(TAG, "Parsing file: " + assetName);
			final InputStream input = context.getAssets().open(assetName);
			if (input != null) {
				ArrayList<Meeting> meetings = decode(input);
				executeBuild(meetings);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	blockRefs.clear();
	}

	public void execute(InputStream stream) throws Exception {
		if (debbug) Log.d(TAG, "Parsing inputStream");
		if (stream != null) {
			ArrayList<Meeting> meetings = decode(stream);
			if (meetings == null || meetings.size() == 0) {
				throw new IOException("Cannot decode inputStream. Not an agenda ? ");
			}
			executeBuild(meetings);
		}
		else {
			throw new IOException("Invalid inputStream."); 
		}
	}

	private void executeBuild(ArrayList<Meeting> meetings) {
		final long versionBuild = System.currentTimeMillis();
		try {
			ArrayList<ContentProviderOperation> batch = transform(meetings, versionBuild);
			Log.d(TAG, "Build database ...");
			mResolver.applyBatch(mAuthority, batch);
			Log.d(TAG, "Build database done");
			ArrayList<ContentProviderOperation> batchClean = purge(versionBuild);
			Log.d(TAG, "Clean database ");
			ContentProviderResult[] results = mResolver.applyBatch(mAuthority, batchClean);
			if (debbug) {
				for (ContentProviderResult r : results) {
					Log.d(TAG, "Result clean : " + r);
				}	
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		blockRefs.clear();
	}

	private ArrayList<ContentProviderOperation> transform(ArrayList<Meeting> meetings, long versionBuild) throws Exception {
		final ArrayList<ContentProviderOperation> batch = Lists.newArrayList();
		for (int i = 0; i < meetings.size(); i++) {
			Meeting m = meetings.get(i);
			ContentProviderOperation cp = createBlock(m, versionBuild); 
			if (cp != null) {
				batch.add(cp);
			}
			cp = createTrack(m, versionBuild);
			if (cp != null) {
				batch.add(cp);
			}
			if (! (m.location.length() == 0)) {
				cp = createRoom(m);
				if (cp != null) {
					batch.add(cp);
				}
			}		
			cp = createSession(m, versionBuild);
			if (cp != null) {
				batch.add(cp);
			}
			cp = createSessionTrack(m);
			if (cp != null) {
				batch.add(cp);
			}
		}
		return batch;
	}
	
	private ContentProviderOperation createBlock(Meeting m, long versionBuild) throws Exception {
		final ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(Blocks.CONTENT_URI);
        builder.withValue(Blocks.UPDATED, versionBuild);
	
		String key = String.format("%s-%s", m.startHour, m.typeSession);
		if (blockRefs.contains(key)) {
			return null;
		}
		else {
			blockRefs.add(key);
		}
	
		String title = null;
		long startTime = -1;
		long endTime = -1;
		String blockType = null;
	
		startTime = ParserUtils.parseTime(m.startHour);
		endTime = ParserUtils.parseTime(m.endHour);
		String blockId = Blocks.generateBlockId(startTime, endTime);

		if (m.typeSession.contains("Registration")) {
			blockType = ParserUtils.BLOCK_TYPE_OFFICE_HOURS;
			title = ParserUtils.BLOCK_TITLE_REGISTRATION;
		}
		else if (m.typeSession.contains("Break")) {
			blockType = ParserUtils.BLOCK_TYPE_FOOD;
			title = m.typeSession;
		}
		else if (m.typeSession.contains("None")) {
			blockType = ParserUtils.BLOCK_TYPE_SESSION;
			title = "...";
		}
		else {
			blockType = ParserUtils.BLOCK_TYPE_SESSION;
			title = m.typeSession.trim().length() == 0 ? m.area : m.typeSession;
		}
		
		builder.withValue(Blocks.BLOCK_ID, blockId);
		builder.withValue(Blocks.BLOCK_TITLE, title);
		builder.withValue(Blocks.BLOCK_START, startTime);
		builder.withValue(Blocks.BLOCK_END, endTime);
		builder.withValue(Blocks.BLOCK_TYPE, blockType);
		return builder.build();	
	}
	
	private ContentProviderOperation createSession(Meeting m, long versionBuild) throws Exception {
		final ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(Sessions.CONTENT_URI);
        builder.withValue(Sessions.UPDATED, versionBuild);

        long startTime = -1;
        long endTime = -1;
        String title = null;
        String sessionId = null;
        String trackId = null;
		String roomId = null;

		try {	
			startTime = ParserUtils.parseTime(m.startHour);
			endTime = ParserUtils.parseTime(m.endHour);
			title = String.format("%s%s%s%s%s", m.group, (m.group.length() == 0 ? "" : " "), m.area, (m.area.length() == 0 ? "" : " "),  m.title);
			roomId = Rooms.generateRoomId(m.location);
			
			sessionId = Sessions.generateSessionId(m.key);
			
			String blockId = Blocks.generateBlockId(startTime, endTime);
		
			builder.withValue(Sessions.SESSION_ID, sessionId);
			builder.withValue(Sessions.SESSION_TITLE, title);
			builder.withValue(Sessions.SESSION_ABSTRACT, null);
			builder.withValue(Sessions.SESSION_URL, m.hrefDetail);
			builder.withValue(Sessions.SESSION_REQUIREMENTS, null);
			builder.withValue(Sessions.SESSION_KEYWORDS, null);
			builder.withValue(Sessions.BLOCK_ID, blockId);
			builder.withValue(Sessions.ROOM_ID, roomId);
			
			final Uri sessionUri = Sessions.buildSessionUri(sessionId);
			final int starred = querySessionStarred(sessionUri, mResolver);
			if (starred != -1) {
				builder.withValue(Sessions.SESSION_STARRED, starred);
			}
			
			return builder.build();
		}
		catch (Exception e) {
			Log.w(TAG, "Error parsing a session involves:[[" + m + "]]");
			e.printStackTrace();
			return null;
		}
	}
	
	private ContentProviderOperation createRoom(Meeting m) throws Exception {
		ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(Rooms.CONTENT_URI);
	
		builder.withValue(Rooms.ROOM_ID, Rooms.generateRoomId(m.location));
		builder.withValue(Rooms.ROOM_NAME, m.location);
		builder.withValue(Rooms.ROOM_FLOOR, " ");
		
		return builder.build();
	}
	
	private ContentProviderOperation createTrack(Meeting m, long versionBuild) throws Exception {
		if (m.group.length() == 0 || m.area.length() == 0) {
			return null;
		}
	
		ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(Tracks.CONTENT_URI);
        builder.withValue(Tracks.UPDATED, versionBuild);
		builder.withValue(Tracks.TRACK_ID, Tracks.generateTrackId(m.group + m.area));
		builder.withValue(Tracks.TRACK_NAME, m.group + "-" + m.area);
		builder.withValue(Tracks.TRACK_COLOR, 1);
		builder.withValue(Tracks.TRACK_ABSTRACT, m.group + "-" + m.area);
	
		return builder.build();
	}

	private ContentProviderOperation createSessionTrack(Meeting m) throws Exception {
		if (m.group.length() == 0 || m.area.length() == 0) {
			return null;
		}
		final String sessionId = Sessions.generateSessionId(m.key);
		final Uri sessionsTracksUri = Sessions.buildTracksDirUri(sessionId);
		ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(sessionsTracksUri);
		builder.withValue(SessionsTracks.SESSION_ID, sessionId);
		builder.withValue(SessionsTracks.TRACK_ID, Tracks.generateTrackId(m.group + m.area));
		
		return builder.build();
	}
	
	/**
	 * Purge the sessions and blocks removed from the agenda. 
	 */
	private ArrayList<ContentProviderOperation> purge(long versionBuild) throws Exception {
		ArrayList<ContentProviderOperation> batchClean = Lists.newArrayList();
		batchClean.add(buildPurge(Sessions.CONTENT_URI, versionBuild));
		batchClean.add(buildPurge(Blocks.CONTENT_URI, versionBuild));
		return batchClean;
	}
	
	private ContentProviderOperation buildPurge(Uri contentURI, long versionBuild) throws Exception {
		ContentProviderOperation.Builder builder = ContentProviderOperation.newDelete(contentURI);
		String where = Sessions.UPDATED + " <> ?";
		String args[] = new String[] {"" + versionBuild };
		builder.withSelection(where, args);
		ContentProviderOperation cp = builder.build();
		return cp;
	}


	private ArrayList<Meeting> decode(final InputStream is) throws IOException {
		final ArrayList<Meeting> meetings = new ArrayList<Meeting>(); 
		DataInputStream reader = new DataInputStream(is);
		boolean ready = true;
		String line = null;
		while (ready) {
			try {   
				line = reader.readLine();
				if (line != null && line.length() != 0) {
					Meeting m = new Meeting(line);
					meetings.add(m);
				}
				else {
					ready = false;
				}	
			}
			catch (IOException e1) {
				ready = false;
				}
			catch (Exception e) {
				Log.w(TAG, "Error parsing line csv file, involves:[[" + line + "]]"); 
				}
			}
		try {
			reader.close();
			}
		catch (Exception e) {
			}
		return meetings;	
		}
            
	public static int querySessionStarred(Uri uri, ContentResolver resolver) {
        final String[] projection = { Sessions.SESSION_STARRED };
        final Cursor cursor = resolver.query(uri, projection, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
			} 
			else {
                return -1;
			}
		}
		finally {
            cursor.close();
		}
	}
}
