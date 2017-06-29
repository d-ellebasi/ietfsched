/*
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

package org.ietf.ietfsched.io;

import java.util.*;
import java.text.*;

import org.ietf.ietfsched.util.UIUtils;


public class Meeting {
	
	final static SimpleDateFormat previousFormat = new SimpleDateFormat("yyyy-MM-d HHmm"); // 2011-07-23 0900 
		// Hack: timezone format (Z) = +0800 where the ietfsched application expects +08:00. 
	final static SimpleDateFormat afterFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:00.000", Locale.US);
	
	String day = " "; // Saturday, March, 12
	String startHour = " "; //2010-05-19T10:45:00.000-07:00
	String endHour = " "; // 2010-05-19T11:45:00.000-07:00
	String title = " ";
	String hrefDetail = " "; 
	String location = "N/A"; // room
	String group = " "; // APP
	String area = " "; // appeara
	String typeSession = ""; // Morning Session I
	String key = ""; // unique identifier

	static {
		previousFormat.setTimeZone(UIUtils.CONFERENCE_TIME_ZONE); 
		afterFormat.setTimeZone(UIUtils.CONFERENCE_TIME_ZONE);
		}
	
	public Meeting(String lineCsv) throws Exception {
		String[] splitted = lineCsv.split("\",\"");
		day = splitted[0].substring(1, splitted[0].length());
		startHour = convert(day, splitted[1]);
		endHour = convert(day, splitted[2]);
		typeSession = splitted[3];
		String tLocation = splitted[4].trim();
		if (tLocation.length() > 0) {
			location = tLocation;
			}
		group = splitted[5];
		area = splitted[6];
		title = splitted[8];
		key = splitted[9];
		hrefDetail = splitted[10];
		}
		
	private static String convert(String date, String hour) throws Exception {
		Date d = previousFormat.parse(String.format("%s %s", date, hour));
		return afterFormat.format(d);
	}
		
	public String toString() {
		return String.format("[%s] [%s] [%s] [%s] [%s] [%s] [%s] [%s] [%s] [%s]", day, startHour, endHour, title, hrefDetail, location, group, area, typeSession, key);
		}
	}
	
