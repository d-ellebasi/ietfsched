/*
 * Copyright 2011 Isabelle Dalmasso
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

package org.ietf.ietfsched.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ScrollView;

/**
 * A fragment that shows the IETF Well Note.
 */
public class WellNoteFragment extends Fragment {
	private static final String WELL_NOTE_TEXT = "\n\n"+
	"Any submission to the IETF intended by the Contributor for publication as all or part of an IETF Internet-Draft "+
	"or RFC and any statement made within the context of an IETF activity is considered an \"IETF Contribution\".\n\n Such statements "+
	"include oral statements in IETF sessions, as well as written and electronic communications made at any time or place, which are addressed to:\n\n"+

    "\t * The IETF plenary session\n"+
    "\t * The IESG, or any member thereof on behalf of the IESG\n"+
    "\t * Any IETF mailing list, including the IETF list itself, any working group or design team list, or any other list functioning under IETF auspices\n"+
    "\t * Any IETF working group or portion thereof\n" +
    "\t * Any Birds of a Feather (BOF) session\n" +
    "\t * The IAB or any member thereof on behalf of the IAB\n" +
    "\t * The RFC Editor or the Internet-Drafts function\n\n" +

	"All IETF Contributions are subject to the rules of RFC 5378 and RFC 3979 (updated by RFC 4879).\n\n"+

	"Statements made outside of an IETF session, mailing list or other function, that are clearly not intended to be input to an IETF "+ 
	"activity, group or function, are not IETF Contributions in the context of this notice.\n\n"+

	"Please consult RFC 5378 and RFC 3979 for details.\n\n"+

	"A participant in any IETF activity is deemed to accept all IETF rules of process, as documented in Best Current Practices RFCs and IESG Statements.\n\n"+
	"A participant in any IETF activity acknowledges that written, audio and video records of meetings may be made and may be available to the public."; 

    private static final String TAG = "WelleNoteFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ScrollView scroller = new ScrollView(getActivity());
		TextView text = new TextView(getActivity());
		text.setText(WELL_NOTE_TEXT);
		scroller.addView(text);
        return scroller;
    }
}
