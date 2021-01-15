/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.core.az;

import android.util.Log;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.ui.*;
import com.biglybt.ui.common.table.impl.TableColumnImpl;
import com.biglybt.ui.common.updater.UIUpdater;
import com.biglybt.ui.mdi.MultipleDocumentInterface;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

class CoreUIFunctions
	implements UIFunctions
{
	public static final String TAG = "CoreUIF";

	private static void loge(String s) {
		if (AndroidUtils.DEBUG) {
			Log.e(TAG, s + "; " + AndroidUtils.getCompressedStackTrace(1, 99));
		}
	}

	@Override
	public String getUIType() {
		loge("getUIType");
		return "Android";
	}

	@Override
	public void bringToFront() {
		loge("bringToFront");
	}

	@Override
	public void bringToFront(boolean tryTricks) {
		loge("bringToFront " + tryTricks);
	}

	@Override
	public int getVisibilityState() {
		loge("getVisibilityState");
		return 0;
	}

	@Override
	public void refreshLanguage() {
		loge("refreshLanguage");

	}

	@Override
	public void refreshIconBar() {
		loge("refreshIconBar");

	}

	@Override
	public void setStatusText(String key) {
		loge("setStatusText " + key);

	}

	@Override
	public void setStatusText(int statustype, String key,
			UIStatusTextClickListener l) {
		loge("setStatusText " + statustype + " " + key + " " + l);

	}

	@Override
	public boolean dispose(boolean for_restart) {
		loge("dispose");

		return false;
	}

	@Override
	public boolean viewURL(String url, String target, int w, int h,
			boolean allowResize, boolean isModal) {
		loge("viewURL " + url + ", " + target + ", " + w + ", " + h + ", "
				+ allowResize + ", " + isModal);

		return false;
	}

	@Override
	public boolean viewURL(String url, String target, double wPct, double hPct,
			boolean allowResize, boolean isModal) {
		loge("viewURL " + url + " " + target + " " + wPct + " " + hPct + " "
				+ allowResize + " " + isModal);

		return false;
	}

	@Override
	public void viewURL(String url, String target, String sourceRef) {
		loge("viewURL " + url + " " + target + " " + sourceRef);

	}

	@Override
	public UIFunctionsUserPrompter getUserPrompter(String title, String text,
			String[] buttons, int defaultOption) {
		loge("getUserPrompter '" + title + "', '" + text + "', " + buttons + " "
				+ defaultOption);

		return null;
	}

	@Override
	public void promptUser(String title, String text, String[] buttons,
			int defaultOption, String rememberID, String rememberText,
			boolean bRememberByDefault, int autoCloseInMS,
			UserPrompterResultListener l) {
		loge("promptUser '" + title + "', '" + text + "', " + buttons + ", "
				+ defaultOption + ", " + rememberID + ", '" + rememberText + "', "
				+ bRememberByDefault + ", " + autoCloseInMS);

	}

	@Override
	public UIUpdater getUIUpdater() {
		loge("getUIUpdater");

		return null;
	}

	@Override
	public void doSearch(String searchText) {
		loge("doSearch " + searchText);

	}

	@Override
	public void doSearch(String searchText, boolean toSubscribe) {
		loge("doSearch '" + searchText + "', " + toSubscribe);

	}

	@Override
	public void installPlugin(String plugin_id, String resource_prefix,
			actionListener listener) {
		loge("installPlugin " + plugin_id + " " + resource_prefix + " " + listener);

	}

	@Override
	public void performAction(int action_id, Object args,
			actionListener listener) {
		loge("performAction " + action_id + ", " + args);

	}

	@Override
	public MultipleDocumentInterface getMDI() {
		loge("getMDI");

		return null;
	}

	@Override
	public void forceNotify(int iconID, String title, String text, String details,
			Object[] relatedObjects, int timeoutSecs) {
		loge("forceNotify '" + iconID + "', '" + title + "', '" + text + "', '"
				+ details + "', " + relatedObjects + ", " + timeoutSecs);

	}

	@Override
	public void runOnUIThread(String ui_type, Runnable runnable) {
		loge("runOnUIThread " + ui_type + " " + runnable);

	}

	@Override
	public boolean isUIThread() {
		loge("isUIThread");

		return false;
	}

	@Override
	public boolean isProgramInstalled(String extension, String name) {
		loge("isProgramInstalled " + extension + ", " + name);

		return false;
	}

	@Override
	public void openRemotePairingWindow() {
		loge("openRemotePairingWindow");

	}

	@Override
	public void playOrStreamDataSource(Object ds, String referal,
			boolean launch_already_checked, boolean complete_only) {
		loge("playOrStreamDataSource " + ds + ", " + referal + ", "
				+ launch_already_checked + ", " + complete_only);

	}

	@Override
	public boolean addTorrentWithOptions(boolean force,
			TorrentOpenOptions torrentOptions) {
		// Copied from com.biglybt.ui.swt.shells.main.UIFunctionsImpl

		Map<String, Object> add_options = new HashMap<>();

		add_options.put(UIFunctions.OTO_FORCE_OPEN, force);

		return (addTorrentWithOptions(torrentOptions, add_options));
	}

	@Override
	public boolean addTorrentWithOptions(TorrentOpenOptions torrentOptions,
			Map<String, Object> addOptions) {
		loge("addTorrentWithOptions " + torrentOptions + ", " + addOptions);

		return torrentOptions.addToDownloadManager();
	}

	@Override
	public void showErrorMessage(String keyPrefix, String details,
			String[] textParams) {
		loge("showErrorMessage '" + keyPrefix + "', '" + details + "', "
				+ textParams);

	}

	@Override
	public void showCreateTagDialog(TagReturner tagReturner) {
		loge("showCreateTagDialog");

	}

	@Override
	public void tableColumnAddedListeners(TableColumnImpl tableColumn,
			Object listeners) {
		loge("tableColumnAddedListeners");

	}

	@Override
	public void copyToClipboard(String text) {
		loge("copyToClipboard " + text);

	}

	@Override
	public void showInExplorer(File f) {
		loge("showInExplorer " + f);

	}

	@Override
	public void showText(String title, String content) {
		loge("showText '" + title + "', '" + content + "'");

	}
}
