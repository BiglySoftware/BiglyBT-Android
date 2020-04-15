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

package com.biglybt.android.client.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.util.SparseArray;
import android.view.ViewGroup;
import android.view.animation.*;
import android.view.animation.Animation.AnimationListener;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.*;
import androidx.preference.EditTextPreference.OnBindEditTextListener;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.client.AndroidUtilsUI;
import com.biglybt.android.client.R;
import com.biglybt.android.client.dialog.DialogFragmentLocationPicker;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker.NumberPickerBuilder;
import com.biglybt.android.client.dialog.DialogFragmentNumberPicker.NumberPickerDialogListener;
import com.biglybt.android.client.rpc.ReplyMapReceivedListener;
import com.biglybt.android.client.session.Session;
import com.biglybt.android.client.session.SessionManager;
import com.biglybt.android.client.session.SessionManager.SessionChangedListener;
import com.biglybt.android.util.*;
import com.biglybt.android.widget.ButtonPreference;
import com.biglybt.android.widget.LabelPreference;
import com.biglybt.android.widget.RadioRowPreference;
import com.biglybt.util.Thunk;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.*;

public class AllPrefFragmentHandler
	implements NumberPickerDialogListener, DefaultLifecycleObserver
{
	private static final String TAG = "AllPrefFragHandler";

	private static final boolean TEST_ADD_DELAY = false; //AndroidUtils.DEBUG;

	// Keep Sorted
	private final static String[] bannedLocalSectionIDs = {
		//"Search", Not in Android Core
		// Backup would work probably, but no way to restore yet
		"backuprestore",
		"logging",
		"plugins.update",
		"plugins.xmwebui",
		// Our proxy section is better
		"proxy",
		"security",
		"sharing",
		// Android code handles AutoStart, sleep, JVM. No hooks yet for restart
		// Could enable Auto-pause/Resume and maybe Shutdown categories
		"startstop",
		"stats",
		"tracker.server",
	};

	private final static String[] bannedLocalKeys = {
		"ConfigView.label.jvm",
		"DefaultDir.AutoUpdate",
		"On Downloading Complete Script",
		"On Seeding Complete Script",
	};

	// Keep Sorted
	private final static String[] bannedRemoteSectionIDs = {
		"Devices",
		"azi2phelper",
		"logging",
	};

	private final static String[] bannedRemoteKeys = {};

	private final static String[] bannedCommonKeys = {
		"File.Decoder.Prompt",
		"File.Decoder.ShowAll",
		"File.Decoder.ShowLax",
		"Monitor Clipboard For Torrents",
		"Network Selection Prompt",
		"Plugin.UPnP.upnp.alertdeviceproblems",
		"Plugin.UPnP.upnp.alertothermappings",
		"Plugin.UPnP.upnp.alertsuccess",
		"Plugin.mlDHT.autoopen.IPv4",
		"Plugin.mlDHT.autoopen.IPv6",
		"Plugin.mlDHT.showStatusEntry",
		"Prompt To Abort Shutdown",
		"def.deletetorrent",
		"diskmanager.perf.cache.trace",
		"network.admin.maybe.vpn.enable",
		"pairing.group.srp",
		"tb.confirm.delete.content",
		"ui.addtorrent.openoptions",
		"ui.addtorrent.openoptions.sep",
		"rcm.show.ftux",
		"rcm.button.sources",
	};

	@NonNull
	private final FragmentActivity activity;

	@Thunk
	@NonNull
	Fragment fragment;

	private PreferenceScreen preferenceScreen;

	private Session session;

	private SessionChangedListener sessionChangedListener;

	@Thunk
	String sectionID;

	private String sectionName;

	private String parentSectionName;

	private String[] bannedSectionIDs;

	@NonNull
	private String[] bannedKeys = {};

	@Thunk
	Map<String, Object> mapSection;

	private Drawable tintedDrawable;

	@Thunk
	AlphaAnimation setAnim;

	private int currentOrderID = 0;

	@Thunk
	boolean reloadingSection = false;

	private long disablingStart;

	@Thunk
	ViewGroup topViewGroup;

	@Thunk
	FrameLayout frameLayout;

	private int requestCode = 0;

	private final SparseArray<Map<String, Object>> mapRequestCodeToParam = new SparseArray<>();

	public AllPrefFragmentHandler(@NonNull Fragment fragment,
			@NonNull PreferenceManager preferenceManager, Bundle savedInstanceState,
			String rootKey) {
		this.activity = fragment.requireActivity();
		this.fragment = fragment;
		Context context = fragment.requireContext();

		sectionID = "root";
		Bundle arguments = fragment.getArguments();
		if (arguments != null) {
			sectionID = arguments.getString("SectionID");
			sectionName = arguments.getString("SectionName");
			parentSectionName = arguments.getString("ParentSectionName");
		}

		if (savedInstanceState != null) {
			sectionID = savedInstanceState.getString("SectionID", sectionID);
			sectionName = savedInstanceState.getString("SectionName", sectionName);
			parentSectionName = savedInstanceState.getString("ParentSectionName",
					parentSectionName);
		}

		preferenceScreen = preferenceManager.createPreferenceScreen(context);
		if (preferenceScreen == null) {
			return;
		}
		preferenceScreen.setOrderingAsAdded(false);
		preferenceScreen.setIconSpaceReserved(false);
		preferenceScreen.setPreferenceDataStore(null);
		preferenceScreen.setPersistent(false);

		Preference preference = new Preference(context);
		preference.setPersistent(false);
		preference.setTitle(R.string.loading);
		preferenceScreen.addPreference(preference);

		fragment.getLifecycle().addObserver(this);

		boolean isRoot = "root".equals(sectionID);

		if (sectionName != null) {
			preferenceScreen.setTitle(AndroidUtils.fromHTML(sectionName));
		}
		if (parentSectionName != null) {
			preferenceScreen.setSummary(parentSectionName);
		}

		if (!isRoot) {
			tintedDrawable = AndroidUtilsUI.getTintedDrawable(context,
					VectorDrawableCompat.create(AndroidUtils.requireResources(activity),
							R.drawable.ic_settings_white_24dp, activity.getTheme()),
					AndroidUtilsUI.getStyleColor(context,
							android.R.attr.textColorPrimary));
		}

		sessionChangedListener = newSession -> session = newSession;
		session = SessionManager.findOrCreateSession(fragment,
				sessionChangedListener);

		reloadSection();
	}

	private void reloadSection() {
		if (reloadingSection) {
			return;
		}
		disableEditing(false);
		session.executeRpc(rpc -> {
			bannedSectionIDs = (session.getRemoteProfile().isLocalHost())
					? bannedLocalSectionIDs : bannedRemoteSectionIDs;
			bannedKeys = (session.getRemoteProfile().isLocalHost()) ? bannedLocalKeys
					: bannedRemoteKeys;

			Map<String, Object> args = new HashMap<>();
			args.put("sections", new String[] {
				sectionID
			});
			rpc.simpleRpcCall("config-get", args, new ReplyMapReceivedListener() {
				@Override
				public void rpcSuccess(String requestID, Map<?, ?> optionalMap) {
					AndroidUtilsUI.runOnUIThread(fragment, false, activity -> {
						Map<String, Object> mapSections = MapUtils.getMapMap(optionalMap,
								"sections", null);
						Map<String, Object> mapSection = MapUtils.getMapMap(mapSections,
								sectionID, null);
						setSection(mapSection, sectionID);
					});
					update();
					reloadingSection = false;
				}

				private void update() {
					reloadingSection = false;
					enableEditing();
				}

				@Override
				public void rpcError(String requestID, Throwable e) {
					update();
				}

				@Override
				public void rpcFailure(String requestID, String message) {
					update();
				}
			});
		});

	}

	@Override
	public void onResume(@NonNull LifecycleOwner owner) {
		reloadingSection = false;

		if (sectionName != null) {
			Toolbar toolbar = activity.findViewById(R.id.actionbar);
			if (toolbar != null) {
				toolbar.setTitle(sectionName);
				toolbar.setSubtitle(parentSectionName);
			}
		}
	}

	@Override
	public void onDestroy(@NonNull LifecycleOwner owner) {
		if (session == null) {
			return;
		}
		SessionManager.removeSessionChangedListener(
				session.getRemoteProfile().getID(), sessionChangedListener);
	}

	@Override
	public void onNumberPickerChange(@Nullable String callbackID, int val) {
		Map<String, Object> parameter = JSONUtils.decodeJSONnoException(callbackID);
		String key = MapUtils.getMapString(parameter, "key", null);
		if (key == null) {
			return;
		}
		setParameter(parameter, key, val);
	}

	public void locationChanged(String callbackID, String location) {
		Map<String, Object> parameter = JSONUtils.decodeJSONnoException(callbackID);
		String key = MapUtils.getMapString(parameter, "key", null);
		if (key == null) {
			return;
		}
		setParameter(parameter, key, location);
	}

	@UiThread
	@Thunk
	void setSection(Map<String, Object> mapSection, String sectionID) {
		this.mapSection = mapSection;
		currentOrderID = 0;

		if (preferenceScreen == null) {
			return;
		}
		if (mapSection == null) {
			// TODO: show message
			return;
		}

		boolean isRoot = "root".equals(sectionID);

		HashMap<String, Preference> mapKeyPreference = getKeyPreferenceMap(
				preferenceScreen);

		//preferenceScreen.removeAll();
		Context context = fragment.requireContext();

		List<Map<String, Object>> subsections = MapUtils.getMapList(mapSection,
				"sub-sections", Collections.emptyList());
		for (Map<String, Object> subsection : subsections) {
			String subSectionID = MapUtils.getMapString(subsection, "id", "");
			if (bannedSectionIDs != null
					&& Arrays.binarySearch(bannedSectionIDs, subSectionID) >= 0) {
				continue;
			}

			Preference preference = mapKeyPreference.remove(subSectionID);
			boolean add = preference == null;
			if (add) {
				preference = new Preference(context);
			}
			preference.setIcon(tintedDrawable);
			String subSectionTitle = MapUtils.getMapString(subsection, "name", "??");
			preference.setTitle(subSectionTitle);
			preference.setKey(subSectionID);
			preference.setFragment(fragment.getClass().getName());
			preference.setPersistent(false);
			Bundle extras = preference.getExtras();
			//noinspection ConstantConditions /* extras always non-null */
			extras.putString("SectionID", subSectionID);
			extras.putString("SectionName", subSectionTitle);
			if (!isRoot) {
				if (parentSectionName == null) {
					extras.putString("ParentSectionName", sectionName);
				} else {
					extras.putString("ParentSectionName",
							parentSectionName + " > " + sectionName);
				}
			}

			preference.setOrder(currentOrderID++);
			if (add) {
				preferenceScreen.addPreference(preference);
			}
		}

		List<Map<String, Object>> parameters = MapUtils.getMapList(mapSection,
				"parameters", Collections.emptyList());
		addParameters(context, preferenceScreen, parameters, mapKeyPreference);
		for (Preference preference : mapKeyPreference.values()) {
			preferenceScreen.removePreference(preference);
		}
	}

	@NonNull
	private HashMap<String, Preference> getKeyPreferenceMap(
			@NonNull PreferenceGroup pg) {
		int preferenceCount = pg.getPreferenceCount();
		HashMap<String, Preference> mapKeyPreference = new HashMap<>();
		for (int i = 0; i < preferenceCount; i++) {
			Preference preference = pg.getPreference(i);
			if (preference == null) {
				continue;
			}
			mapKeyPreference.put(preference.getKey(), preference);
		}

		return mapKeyPreference;
	}

	@UiThread
	private void addParameters(@NonNull Context context,
			@NonNull PreferenceGroup preferenceGroup,
			@NonNull List<Map<String, Object>> parameters,
			@NonNull HashMap<String, Preference> mapKeyPreference) {
		for (int i = 0, parametersSize = parameters.size(); i < parametersSize; i++) {
			Map<String, Object> parameter = parameters.get(i);
			if (parameter == null) {
				continue;
			}
			String type = MapUtils.getMapString(parameter, "type", "");
			if ("group".equalsIgnoreCase(type)) {
				List<Map<String, Object>> groupParams = MapUtils.getMapList(parameter,
						"parameters", Collections.emptyList());
				String groupID = MapUtils.getMapString(parameter, "id", "");
				if (!groupParams.isEmpty()
						&& Arrays.binarySearch(bannedCommonKeys, groupID) < 0
						&& Arrays.binarySearch(bannedKeys, groupID) < 0) {
					PreferenceGroup group = addGroupParameter(context, preferenceGroup,
							parameter, mapKeyPreference);
					HashMap<String, Preference> keyPreferenceMap = getKeyPreferenceMap(
							group);
					addParameters(context, group, groupParams, keyPreferenceMap);
					for (Preference preference : keyPreferenceMap.values()) {
						group.removePreference(preference);
					}
				}
			} else {
				String key = MapUtils.getMapString(parameter, "key", null);
				if (key == null) {
					key = "nokey-" + preferenceGroup.getKey() + "-" + i;
				}

				addParameter(context, preferenceGroup, parameter, key, type,
						mapKeyPreference);
			}
		}
	}

	@NonNull
	private PreferenceGroup addGroupParameter(@NonNull Context context,
			@NonNull PreferenceGroup parent, Map<String, Object> parameter,
			@NonNull HashMap<String, Preference> mapKeyPreference) {
		String key = MapUtils.getMapString(parameter, "id", null);

		Preference existing = mapKeyPreference.remove(key);
		boolean add = !(existing instanceof PreferenceGroup);
		PreferenceCategory preference = add ? new PreferenceCategory(context)
				: (PreferenceCategory) existing;

		preference.setIconSpaceReserved(false);
		preference.setPersistent(false);
		preference.setKey(key);
		preference.setTitle(MapUtils.getMapString(parameter, "title", ""));
		preference.setOrder(currentOrderID++);
		if (add) {
			parent.addPreference(preference);
		}
		return preference;
	}

	@UiThread
	private void addParameter(@NonNull Context context,
			@NonNull PreferenceGroup preferenceGroup,
			@NonNull Map<String, Object> parameter, @NonNull String key,
			@NonNull String type,
			@NonNull HashMap<String, Preference> mapKeyPreference) {

		if (Arrays.binarySearch(bannedKeys, key) >= 0
				|| Arrays.binarySearch(bannedCommonKeys, key) >= 0) {
			return;
		}

		Preference preference = mapKeyPreference.remove(key);
		boolean add = preference == null;

		Object value = parameter.get("val");
		String label = MapUtils.getMapString(parameter, "label", null);
		boolean enabled = MapUtils.getMapBoolean(parameter, "enabled", true);

		CharSequence summary = null;
		boolean skipSetPrefChangeListener = false;
		boolean doStandardSummary = true;

		switch (type.toLowerCase()) {
			case "boolean":
				SwitchPreferenceCompat switchPref = add
						? new SwitchPreferenceCompat(context)
						: (SwitchPreferenceCompat) preference;
				preference = switchPref;
				switchPref.setChecked(MapUtils.getMapBoolean(parameter, "val", false));
				doStandardSummary = false;
				break;

			case "hyperlink": {
				if (add) {
					preference = new Preference(context);
				}
				String url = MapUtils.getMapString(parameter, "hyperlink", null);
				String title = MapUtils.getMapString(parameter, "hyperlink-title", url);
				if (url != null) {
					doStandardSummary = false;
					String titleEncoded = TextUtils.htmlEncode(title);
					summary = AndroidUtils.fromHTML(
							enabled ? "<A HREF=\"" + url + "\">" + titleEncoded + "</url>"
									: "<u>" + titleEncoded + "</u>");
					preference.setOnPreferenceClickListener(pref -> {
						AndroidUtilsUI.openURL(activity, url, title);
						return true;
					});
				}
				break;
			}

			case "action": {
				String actionID = MapUtils.getMapString(parameter, "action-id", null);
				if (actionID != null
						&& Arrays.binarySearch(bannedCommonKeys, actionID) >= 0) {
					return;
				}

				skipSetPrefChangeListener = true;

				String style = MapUtils.getMapString(parameter, "style", "");
				if ("link".equalsIgnoreCase(style)) {
					if (add) {
						preference = new Preference(context);
					}
				} else {
					ButtonPreference bp = add ? new ButtonPreference(context)
							: (ButtonPreference) preference;
					preference = bp;
					bp.setRowClickable(false);
					bp.setButtonText(MapUtils.getMapString(parameter, "text", ""));
					bp.setOnPreferenceClickListener(pref -> {
						sendAction(parameter);
						return true;
					});
				}
				break;
			}

			case "label":
			case "info":
				if (add) {
					preference = new LabelPreference(context);
				}
				break;

			case "stringlist":
			case "intlist": {
				skipSetPrefChangeListener = true;

				String style = MapUtils.getMapString(parameter, "style", "");
				List<String> labels = MapUtils.getMapList(parameter, "labels",
						Collections.emptyList());
				List<Object> vals = MapUtils.getMapList(parameter, "vals",
						Collections.emptyList());

				String[] entries = labels.toArray(new String[0]);
				String displayValue = null;

				if ("radiocompact".equalsIgnoreCase(style)) {
					RadioRowPreference rp = add ? new RadioRowPreference(context)
							: (RadioRowPreference) preference;
					preference = rp;
					rp.setOnPreferenceRadioClickListener((position, pref) -> {
						if (position >= vals.size() || position < 0) {
							return;
						}
						setParameter(parameter, pref.getKey(), vals.get(position));
					});

					rp.setEntries(entries);
					for (int i = 0, valsSize = vals.size(); i < valsSize; i++) {
						Object v = vals.get(i);
						if (v.equals(value)) {
							rp.setValueIndex(i);
							displayValue = entries[i];
							break;
						}
					}
				} else {
					ListPreference lp = add ? new ListPreference(context)
							: (ListPreference) preference;
					preference = lp;

					lp.setEntries(entries);
					lp.setEntryValues(entries); // Only needed because it's required
					for (int i = 0, valsSize = vals.size(); i < valsSize; i++) {
						Object v = vals.get(i);
						if (v.equals(value)) {
							lp.setValueIndex(i);
							displayValue = entries[i];
							break;
						}
					}
					lp.setOnPreferenceChangeListener((pref, newValue) -> {
						int i = ((ListPreference) pref).findIndexOfValue((String) newValue);
						if (i >= 0) {
							setParameter(parameter, pref.getKey(), vals.get(i));
						}
						return true;
					});
				}

				if (displayValue != null) {
					doStandardSummary = false;
					String summaryString = displayValue;
					String labelSuffix = MapUtils.getMapString(parameter, "label-suffix",
							"");
					if (!labelSuffix.isEmpty()) {
						summaryString = summaryString + " " + labelSuffix;
					}

					// "Feature" in ListPreference: Tries this on summary, breaking normal % with UnknownFormatConversionException
					// String formattedString = String.format(mSummary, entry == null ? "" : entry);
					summary = summaryString.replace("%", "%%");
				}
			}
				break;

			case "int": {
				if (add) {
					preference = new Preference(context);
				}
				preference.setOnPreferenceClickListener(pref -> {
					String callbackID = JSONUtils.encodeToJSON(parameter);
					NumberPickerBuilder builder = new NumberPickerBuilder(
							fragment.getFragmentManager(), callbackID,
							((Number) value).intValue());
					builder.setTargetFragment(fragment);
					Object min = parameter.get("min");
					if (min instanceof Number) {
						builder.setMin(((Number) min).intValue());
					}
					Object max = parameter.get("max");
					if (max instanceof Number) {
						int maxInt = ((Number) max).intValue();
						builder.setMax(maxInt);
						builder.setShowSpinner(maxInt <= 100);
					} else {
						builder.setShowSpinner(false);
					}
					builder.setTitle(null);
					builder.setSubtitle(label);
					String labelSuffix = MapUtils.getMapString(parameter, "label-suffix",
							"");
					if (!labelSuffix.isEmpty()) {
						builder.setSuffix(labelSuffix);
					}
					DialogFragmentNumberPicker.openDialog(builder);
					return true;
				});
				break;
			}

			case "string": {
				skipSetPrefChangeListener = true;
				preference = createOrFillEditTextPreference(context, preference,
						parameter, editText -> {
							String validChars = MapUtils.getMapString(parameter,
									"valid-chars", null);
							if (validChars != null) {
								boolean caseSensitive = MapUtils.getMapBoolean(parameter,
										"valid-case-sensitive", true);
								if (!caseSensitive) {
									validChars = validChars.toUpperCase()
											+ validChars.toLowerCase();
									// We could remove duplicates, but is it worth it?
								}
								editText.setKeyListener(
										DigitsKeyListener.getInstance(validChars));
							}
							int maxLength = MapUtils.getMapInt(parameter, "width-hint", 0);
							if (maxLength > 0) {
								editText.setFilters(new InputFilter[] {
									new InputFilter.LengthFilter(maxLength)
								});
							}
							int multiline = MapUtils.getMapInt(parameter, "multiline", 0);
							editText.setSingleLine(multiline == 0);
							if (multiline > 0) {
								editText.setMinLines(multiline);
							}
						}, (pref, newValue) -> {
							setParameter(parameter, key, newValue);
							return true;
						});

				break;
			}

			case "directory": {
				if (add) {
					preference = new Preference(context);
				}
				preference.setWidgetLayoutResource(
						R.layout.preference_widget_foldericon);
				skipSetPrefChangeListener = true;
				String startDir = (value instanceof String) ? (String) value : "";
				preference.setOnPreferenceClickListener(pref -> {
					String callbackID = JSONUtils.encodeToJSON(parameter);
					DialogFragmentLocationPicker.openDialogChooser(callbackID, startDir,
							session, fragment.getFragmentManager(), fragment);
					return true;
				});
				doStandardSummary = false;
				Preference finalPreference = preference;
				if (!startDir.isEmpty()) {
					AndroidUtilsUI.runOffUIThread(() -> {
						CharSequence s = FileUtils.buildPathInfo(context,
								new File(startDir)).getFriendlyName(context);

						AndroidUtilsUI.runOnUIThread(fragment, false,
								activity -> finalPreference.setSummary(s));
					});
				}

				break;
			}

			case "file": {
				if (add) {
					preference = new Preference(context);
				}
				preference.setWidgetLayoutResource(
						R.layout.preference_widget_foldericon);
				skipSetPrefChangeListener = true;
				preference.setOnPreferenceClickListener(pref -> {
					if (session.getRemoteProfile().isLocalHost()) {
						//TODO: figure out extensions
						//MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
						mapRequestCodeToParam.put(requestCode, parameter);
						FileUtils.openFileChooser(activity, fragment, "*/*", requestCode);
						requestCode++;
					} else {
						AndroidUtilsUI.createTextBoxDialog(context, label, null, null,
								(String) value, EditorInfo.IME_ACTION_DONE,
								InputType.TYPE_CLASS_TEXT,
								(dialog, which, editText) -> setParameter(parameter, key,
										editText.getText().toString())).show();
					}
					return true;
				});
				break;
			}

			case "float": {
				skipSetPrefChangeListener = true;
				preference = createOrFillEditTextPreference(context, preference,
						parameter, editText -> {
							editText.setInputType(InputType.TYPE_CLASS_NUMBER
									| InputType.TYPE_NUMBER_FLAG_DECIMAL);
							int maxLength = MapUtils.getMapInt(parameter, "width-hint", 0);
							if (maxLength > 0) {
								editText.setFilters(new InputFilter[] {
									new InputFilter.LengthFilter(maxLength)
								});
							}
						}, (pref, newValue) -> {
							try {
								float f = Float.parseFloat((String) newValue);
								setParameter(parameter, key, f);
							} catch (Throwable t) {
								new MaterialAlertDialogBuilder(activity).setMessage(
										t.getMessage()).setCancelable(true).setTitle(label).show();
							}
							return true;
						});
				break;
			}

			case "password": {
				preference = createOrFillEditTextPreference(context, preference,
						parameter,
						editText -> editText.setInputType(InputType.TYPE_CLASS_TEXT
								| InputType.TYPE_TEXT_VARIATION_PASSWORD),
						(pref, newValue) -> {
							setParameter(parameter, key, newValue);
							return true;
						});
				break;
			}

			default:
				if (add) {
					preference = new Preference(context);
					preference.setTitle(label);
				}
				break;
		}

		if (doStandardSummary) {
			summary = "";
			if (value != null) {
				summary = value.toString();
				String labelSuffix = MapUtils.getMapString(parameter, "label-suffix",
						"");
				if (!labelSuffix.isEmpty()) {
					summary = summary + " " + labelSuffix;
				}
			}
			String tooltip = MapUtils.getMapString(parameter, "label-tooltip", "");
			if (!tooltip.isEmpty()) {
				if (summary.length() > 0) {
					summary = summary + "\n";
				}
				summary = summary + tooltip;
			}
		}

		preference.setPersistent(false);
		preference.setIconSpaceReserved(false);
		preference.setSingleLineTitle(false);
		int indent = MapUtils.getMapInt(parameter, "indent", 0);
		if (indent > 0) {
			preference.setIcon(R.drawable.pref_indent);
		}

		if (label == null || label.isEmpty()) {
			preference.setTitle(summary);
		} else {
			preference.setTitle(AndroidUtils.fromHTML(label));
			preference.setSummary(summary);
		}

		if (preference instanceof DialogPreference) {
			DialogPreference dp = (DialogPreference) preference;
			dp.setDialogTitle(AndroidUtils.fromHTML(
					MapUtils.getMapString(parameter, "dialog-title", label)));
			dp.setDialogMessage(MapUtils.getMapString(parameter, "dialog-message",
					MapUtils.getMapString(parameter, "label-tooltip", null)));
		}

		preference.setKey(key);
		preference.setEnabled(enabled);
		if (enabled && !skipSetPrefChangeListener) {
			preference.setOnPreferenceChangeListener((pref, newValue) -> {
				setParameter(parameter, pref.getKey(), newValue);
				return true;
			});
		}

		preference.setOrder(currentOrderID++);
		if (add) {
			preferenceGroup.addPreference(preference);
		}
	}

	private void sendAction(Map<String, Object> parameter) {

		String actionID = MapUtils.getMapString(parameter, "action-id", null);
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "addParameter: action clicked " + actionID);
		}

		if (actionID == null) {
			return;
		}
		session.executeRpc(rpc -> {
			Map<String, Object> args = new HashMap<>();
			args.put("action-id", actionID);
			args.put("section-id", sectionID);
			rpc.simpleRpcCall("config-action", args, new ReplyMapReceivedListener() {
				@Override
				public void rpcSuccess(String requestID, Map<?, ?> optionalMap) {
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "addParameter: action clicked " + actionID + ". done");
					}
					Map<String, Object> mapNewSections = MapUtils.getMapMap(optionalMap,
							sectionID, null);
					if (mapNewSections != null) {
						mapSection = mapNewSections;
						AndroidUtilsUI.runOnUIThread(fragment, false,
								(activity) -> setSection(mapSection, sectionID));
					}

				}

				@Override
				public void rpcError(String requestID, Throwable e) {
					if (AndroidUtils.DEBUG) {
						Log.w(TAG, "addParameter: action clicked " + actionID + "; " + e);
					}

				}

				@Override
				public void rpcFailure(String requestID, String message) {
					if (AndroidUtils.DEBUG) {
						Log.w(TAG,
								"addParameter: action clicked " + actionID + "; " + message);
					}
				}
			});
		});
	}

	/**
	 * Since EditTextPreference on Leanback 
	 * ({@link androidx.leanback.preference.LeanbackEditTextPreferenceDialogFragmentCompat} v1.1.0)
	 * doesn't trigger {@link OnPreferenceChangeListener}, we use our own
	 * popup text dialog for it, and a standard EditTextPreference for everyone
	 * else.
	 */
	@NonNull
	private static Preference createOrFillEditTextPreference(
			@NonNull Context context, @Nullable Preference preference,
			@NonNull Map<String, Object> parameter,
			@Nullable OnBindEditTextListener onBindEditTextListener,
			@NonNull OnPreferenceChangeListener onPreferenceChangeListener) {

		String value = MapUtils.getMapString(parameter, "val", "");

		if (AndroidUtils.isLiterallyLeanback(context)) {
			CharSequence label = MapUtils.getMapString(parameter, "dialog-title",
					MapUtils.getMapString(parameter, "label", null));
			if (label != null) {
				label = AndroidUtils.fromHTML((String) label);
			}

			if (preference == null
					|| !preference.getClass().equals(Preference.class)) {
				preference = new Preference(context);
			}

			Preference finalPreference = preference;
			CharSequence finalLabel = label;
			preference.setOnPreferenceClickListener((pref) -> {
				AlertDialog textBoxDialog = AndroidUtilsUI.createTextBoxDialog(context,
						finalLabel, null, null, value, EditorInfo.IME_ACTION_DONE,
						EditorInfo.TYPE_CLASS_TEXT,
						(dialog, which,
								editText) -> onPreferenceChangeListener.onPreferenceChange(
										finalPreference, editText.getText().toString()));

				if (onBindEditTextListener != null) {
					TextInputEditText textView = textBoxDialog.findViewById(
							R.id.textInputEditText);
					if (textView != null) {
						onBindEditTextListener.onBindEditText(textView);
					}
				}

				textBoxDialog.show();
				return true;
			});
		} else {
			EditTextPreference etp = preference instanceof EditTextPreference
					? (EditTextPreference) preference : new EditTextPreference(context);
			preference = etp;
			etp.setText(value);
			if (onBindEditTextListener != null) {
				etp.setOnBindEditTextListener(onBindEditTextListener);
			}

			preference.setOnPreferenceChangeListener(onPreferenceChangeListener);

			// caller will handle dialog title/text since we are of
			// PreferenceDialogFragment
		}
		return preference;
	}

	private void setParameter(Map<String, Object> parameter, String key,
			Object newValue) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "setParameter: `" + key + "` -> " + newValue + " ("
					+ (newValue == null ? "null" : newValue.getClass().getSimpleName())
					+ ")");
		}

		disableEditing(true);

		session.executeRpc(rpc -> {
			Map<String, Object> args = new HashMap<>();
			Map<String, Object> parameters = new HashMap<>();

			args.put("parameters", parameters);
			//parameters.put(callbackID, Collections.EMPTY_LIST);
			parameters.put(key, newValue);
			rpc.simpleRpcCall("config-set", args, new ReplyMapReceivedListener() {
				@Override
				public void rpcSuccess(String requestID, Map<?, ?> optionalMap) {
					Map<String, Object> successParamaters = MapUtils.getMapMap(
							optionalMap, "success", Collections.emptyMap());
					Map<String, Object> errorParameters = MapUtils.getMapMap(optionalMap,
							"error", Collections.emptyMap());
					Map<String, Object> sections = MapUtils.getMapMap(optionalMap,
							"sections", Collections.emptyMap());

					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "rpcSuccess: error=" + errorParameters);
					}

					String error = MapUtils.getMapString(errorParameters, key, null);
					if (error != null) {
						AndroidUtilsUI.runOnUIThread(fragment, false, (activity) -> {
							AlertDialog.Builder builder = new MaterialAlertDialogBuilder(
									activity);
							builder.setTitle(MapUtils.getMapString(parameter, "label", null));
							builder.setMessage(error);
							builder.setPositiveButton(android.R.string.ok, null);
							builder.show();
						});
					}

					Map<String, Object> mapNewSections = MapUtils.getMapMap(sections,
							sectionID, null);
					if (mapNewSections != null) {
						mapSection = mapNewSections;
					}

					AndroidUtilsUI.runOnUIThread(fragment, false,
							(activity) -> setSection(mapSection, sectionID));
					update();
				}

				@Override
				public void rpcError(String requestID, Throwable e) {
					AndroidUtilsUI.runOnUIThread(fragment, false, (activity) -> {
						AlertDialog.Builder builder = new MaterialAlertDialogBuilder(
								activity);
						builder.setTitle(MapUtils.getMapString(parameter, "label", null));
						builder.setMessage(e.toString());
						builder.setPositiveButton(android.R.string.ok, null);
						builder.show();
					});
					update();
				}

				@Override
				public void rpcFailure(String requestID, String message) {
					AndroidUtilsUI.runOnUIThread(fragment, false, (activity) -> {
						AlertDialog.Builder builder = new MaterialAlertDialogBuilder(
								activity);
						builder.setTitle(MapUtils.getMapString(parameter, "label", null));
						builder.setMessage(message);
						builder.setPositiveButton(android.R.string.ok, null);
						builder.show();
					});
					update();
				}

				private void update() {
					enableEditing();
				}
			});
		});
	}

	@Thunk
	void enableEditing() {
		if (topViewGroup == null) {
			return;
		}
		if (TEST_ADD_DELAY) {
			try {
				long millis = (long) (Math.random() * 1100);
				Log.e(TAG, "ma=; sleep " + millis);
				Thread.sleep(millis);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		AndroidUtilsUI.runOnUIThread(fragment, false, (activity) -> {
			if (setAnim != null) {
				Transformation tf = new Transformation();
				setAnim.getTransformation(AnimationUtils.currentAnimationTimeMillis(),
						tf);
				float alpha = tf.getAlpha();
				topViewGroup.clearAnimation();
				setAnim.cancel();
				setAnim = null;

				int duration = (int) Math.max(
						(System.currentTimeMillis() - disablingStart) / 3, 500);
				if (alpha < 1.0f && duration > 10) {
					AlphaAnimation alphaAnimation = new AlphaAnimation(alpha, 1.0f);
					alphaAnimation.setInterpolator(new AccelerateInterpolator());
					alphaAnimation.setDuration(duration);
					alphaAnimation.setFillAfter(true);
					alphaAnimation.setAnimationListener(new AnimationListener() {
						@Override
						public void onAnimationStart(Animation animation) {

						}

						@Override
						public void onAnimationEnd(Animation animation) {
							topViewGroup.removeView(frameLayout);
						}

						@Override
						public void onAnimationRepeat(Animation animation) {

						}
					});
					topViewGroup.startAnimation(alphaAnimation);
					return;
				}
			}
			topViewGroup.removeView(frameLayout);
		});
	}

	private void disableEditing(boolean fade) {
		ViewGroup view = (ViewGroup) fragment.getView();
		if (view == null) {
			topViewGroup = activity.findViewById(android.R.id.list_container);
			if (topViewGroup == null) {
				topViewGroup = AndroidUtilsUI.getContentView(activity);
			}
		} else {
			topViewGroup = view.findViewById(android.R.id.list_container);
		}

		if (setAnim != null) {
			setAnim.cancel();
			setAnim = null;
		}

		frameLayout = null;
		if (topViewGroup != null) {
			frameLayout = new FrameLayout(fragment.requireContext());
			frameLayout.setClickable(true);
			frameLayout.setFocusable(true);
			topViewGroup.addView(frameLayout);
			frameLayout.bringToFront();
			frameLayout.requestFocus();

			if (fade) {
				setAnim = new AlphaAnimation(1.0f, 0.2f);
				setAnim.setInterpolator(new DecelerateInterpolator());
				setAnim.setDuration(1500);
				setAnim.setFillAfter(true);
				setAnim.setFillEnabled(true);
				topViewGroup.startAnimation(setAnim);
			}
		}

		disablingStart = System.currentTimeMillis();
	}

	public void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putString("SectionID", sectionID);
		outState.putString("SectionName", sectionName);
		outState.putString("ParentSectionName", parentSectionName);
	}

	public PreferenceScreen getPreferenceScreen() {
		return preferenceScreen;
	}

	public void onActivityResult(int requestCode, int resultCode,
			@Nullable Intent intent) {
		if (resultCode != Activity.RESULT_OK || intent == null) {
			return;
		}
		Map<String, Object> parameter = mapRequestCodeToParam.get(resultCode);
		String key = MapUtils.getMapString(parameter, "key", null);
		if (key == null) {
			return;
		}
		Uri uri = intent.getData();
		if (uri == null) {
			return;
		}
		String path = PaulBurkeFileUtils.getPath(activity, uri);
		if (path == null) {
			return;
		}
		setParameter(parameter, key, path);
	}
}
