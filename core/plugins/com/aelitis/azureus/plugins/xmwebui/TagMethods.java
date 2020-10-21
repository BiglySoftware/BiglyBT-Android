/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
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

package com.aelitis.azureus.plugins.xmwebui;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.biglybt.core.category.Category;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys.*;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.util.MapUtils;

import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;

import static com.aelitis.azureus.plugins.xmwebui.StaticUtils.canAdd;
import static com.aelitis.azureus.plugins.xmwebui.TransmissionVars.*;

public class TagMethods
{

	private final Map<String, TagSearchInstance> active_tagsearches = new HashMap<>();

	public void getList(Map<String, Object> args, Map<String, Object> result) {
		List<String> fields = (List<String>) args.get(ARG_FIELDS);
		boolean all = fields == null || fields.size() == 0;
		if (!all) {
			// sort so we can't use Collections.binarySearch
			Collections.sort(fields);
		}

		List<SortedMap<String, Object>> listTags = new ArrayList<>();

		TagManager tm = TagManagerFactory.getTagManager();

		List<TagType> tagTypes = tm.getTagTypes();

		for (TagType tagType : tagTypes) {
			List<Tag> tags = tagType.getTags();

			for (Tag tag : tags) {
				listTags.add(buildTagMap(tag, tagType, fields, all));
			}
		}

		String hc = Long.toHexString(StaticUtils.longHashSimpleList(listTags));
		result.put("tags-hc", hc);

		String oldHC = MapUtils.getMapString(args, "tags-hc", null);
		if (!hc.equals(oldHC)) {
			result.put("tags", listTags);
		}
	}

	private SortedMap<String, Object> buildTagMap(Tag tag, TagType tagType,
			List<String> fields, boolean all) {
		SortedMap<String, Object> map = new TreeMap<>();
		if (canAdd(FIELD_TAG_NAME, fields, all)) {
			map.put(FIELD_TAG_NAME, tag.getTagName(true));
		}

		//map.put("taggableTypes", tag.getTaggableTypes()); // com.aelitis.azureus.core.tag.Taggable
		if (canAdd(FIELD_TAG_COUNT, fields, all)) {
			map.put(FIELD_TAG_COUNT, tag.getTaggedCount());
		}
		if (canAdd(FIELD_TAG_TYPE, fields, all)) {
			map.put(FIELD_TAG_TYPE, tagType.getTagType());
		}
		if (canAdd(FIELD_TAG_TYPENAME, fields, all)) {
			map.put(FIELD_TAG_TYPENAME, tagType.getTagTypeName(true));
		}

		if (canAdd(FIELD_TAG_CATEGORY_TYPE, fields, all)) {
			if (tag instanceof Category) {
				map.put(FIELD_TAG_CATEGORY_TYPE, ((Category) tag).getType());
			}
		}
		if (canAdd(FIELD_TAG_UID, fields, all)) {
			map.put(FIELD_TAG_UID, tag.getTagUID());
		}
		if (canAdd(FIELD_TAG_ID, fields, all)) {
			map.put(FIELD_TAG_ID, tag.getTagID());
		}
		if (canAdd(FIELD_TAG_COLOR, fields, all)) {
			int[] color = tag.getColor();
			if (color != null) {
				String hexColor = "#";
				for (int c : color) {
					if (c < 0x10) {
						hexColor += "0";
					}
					hexColor += Integer.toHexString(c);
				}
				map.put(FIELD_TAG_COLOR, hexColor);
			}
		}
		if (canAdd(FIELD_TAG_CANBEPUBLIC, fields, all)) {
			map.put(FIELD_TAG_CANBEPUBLIC, tag.canBePublic());
		}
		if (canAdd(FIELD_TAG_PUBLIC, fields, all)) {
			map.put(FIELD_TAG_PUBLIC, tag.isPublic());
		}
		if (canAdd(FIELD_TAG_VISIBLE, fields, all)) {
			map.put(FIELD_TAG_VISIBLE, tag.isVisible());
		}
		if (canAdd(FIELD_TAG_GROUP, fields, all)) {
			map.put(FIELD_TAG_GROUP, tag.getGroup());
		}
		if (all || Collections.binarySearch(fields, FIELD_TAG_AUTO_ADD) >= 0
				|| Collections.binarySearch(fields, FIELD_TAG_AUTO_REMOVE) >= 0) {
			boolean[] auto = tag.isTagAuto();
			if (canAdd(FIELD_TAG_AUTO_ADD, fields, all)) {
				map.put(FIELD_TAG_AUTO_ADD, auto[0]);
			}
			if (canAdd(FIELD_TAG_AUTO_REMOVE, fields, all)) {
				map.put(FIELD_TAG_AUTO_REMOVE, auto[1]);
			}
		}

		if (canAdd(FIELD_TAG_CONSTRAINT, fields, all)
				&& tag.getTagType().hasTagTypeFeature(TagFeature.TF_PROPERTIES)
				&& (tag instanceof TagFeatureProperties)) {
			TagFeatureProperties tfp = (TagFeatureProperties) tag;

			final TagProperty propConstraint = tfp.getProperty(
					TagFeatureProperties.PR_CONSTRAINT);
			if (propConstraint != null) {
				String[] stringList = propConstraint.getStringList();
				// constraint only has one entry
				if (stringList.length > 0 && stringList[0] != null
						&& !stringList[0].isEmpty()) {
					Map<String, Object> mapConstraint = new LinkedHashMap<>();

					map.put(FIELD_TAG_CONSTRAINT, mapConstraint);

					mapConstraint.put("text", stringList[0]);

					if (stringList.length > 1 && stringList[1] != null) {
						mapConstraint.put("mode", stringList[1]);
					} else {
						mapConstraint.put("mode", "am=0;");
					}

					mapConstraint.put("enabled", propConstraint.isEnabled());

					String error = (String) tag.getTransientProperty(
							Tag.TP_CONSTRAINT_ERROR);
					if (error != null) {
						mapConstraint.put("error", error);
					}
				}
			}
		}

		if (canAdd(FIELD_TAG_FILE_LOCATION, fields, all)) {
			if (tag instanceof TagFeatureFileLocation) {
				TagFeatureFileLocation fl = (TagFeatureFileLocation) tag;

				Map<String, Object> mapFL = new LinkedHashMap<>();
				map.put(FIELD_TAG_FILE_LOCATION, mapFL);

				if (fl.supportsTagInitialSaveFolder() && addIfNotNull(mapFL,
						"initial-save-folder", fl.getTagInitialSaveFolder())) {
					mapFL.put("initial-save-folder-flags", fl.getTagInitialSaveOptions());
				}
				if (fl.supportsTagMoveOnComplete() && addIfNotNull(mapFL,
						"move-on-complete", fl.getTagMoveOnCompleteFolder())) {
					mapFL.put("move-on-complete-flags", fl.getTagMoveOnCompleteOptions());
				}
				if (fl.supportsTagCopyOnComplete() && addIfNotNull(mapFL,
						"copy-on-complete", fl.getTagCopyOnCompleteFolder())) {
					mapFL.put("copy-on-complete-flags", fl.getTagCopyOnCompleteOptions());
				}
				if (fl.supportsTagMoveOnRemove() && addIfNotNull(mapFL,
						"move-on-remove", fl.getTagMoveOnRemoveFolder())) {
					mapFL.put("move-on-remove-flags", fl.getTagMoveOnRemoveOptions());
				}
			}
		}

		if (canAdd(FIELD_TAG_LIMIT, fields, all)) {
			if (tag.getTagType().hasTagTypeFeature(TagFeature.TF_LIMITS)
					&& tag instanceof TagFeatureLimits) {
				Map<String, Object> mapL = new LinkedHashMap<>();
				map.put(FIELD_TAG_LIMIT, mapL);

				TagFeatureLimits tfl = (TagFeatureLimits) tag;

				int maxTaggables = tfl.getMaximumTaggables();
				if (maxTaggables >= 0) {
					mapL.put("max-taggables", maxTaggables);
					/*
					public static final int RS_NONE						= 0;
					public static final int RS_ARCHIVE					= 1;
					public static final int RS_REMOVE_FROM_LIBRARY		= 2;
					public static final int RS_DELETE_FROM_COMPUTER		= 3;
					public static final int RS_MOVE_TO_OLD_TAG			= 4;
					 */
					mapL.put("removal-strategy", tfl.getRemovalStrategy());
					/*
					public static final int OP_ADDED_TO_VUZE			= 0;
					public static final int OP_ADED_TO_TAG				= 1;
					 */
					mapL.put("ordering", tfl.getOrdering());
				}

			}
		}

		if (canAdd(FIELD_TAG_TRANSFER, fields, all)) {
			if (tag instanceof TagFeatureRateLimit) {
				TagFeatureRateLimit rl = (TagFeatureRateLimit) tag;

				Map<String, Object> mapT = new LinkedHashMap<>();
				map.put(FIELD_TAG_TRANSFER, mapT);

				if (rl.supportsTagDownloadLimit()) {
					mapT.put("download-limit", rl.getTagDownloadLimit()); // b/s
				}
				if (rl.supportsTagUploadLimit()) {
					mapT.put("upload-limit", rl.getTagUploadLimit()); // b/s
				}
				if (rl.supportsTagRates()) {
					mapT.put("current-download-rate", rl.getTagCurrentDownloadRate());
					mapT.put("current-upload-rate", rl.getTagCurrentUploadRate());
					mapT.put("download-session", rl.getTagSessionDownloadTotal());
					mapT.put("upload-session", rl.getTagSessionUploadTotal());
					mapT.put("download-total", rl.getTagDownloadTotal());
					mapT.put("upload-total", rl.getTagUploadLimit());
				}

				int tagUploadPriority = rl.getTagUploadPriority();
				if (tagUploadPriority > 0) {
					mapT.put("upload-priority", tagUploadPriority);
				}

				int tagMinShareRatio = rl.getTagMinShareRatio();
				if (tagMinShareRatio > 0) {
					mapT.put("min-sr", tagMinShareRatio);
				}

				int tagMaxShareRatio = rl.getTagMaxShareRatio();
				if (tagMaxShareRatio > 0) {
					mapT.put("max-sr", tagMinShareRatio);
					/*
					public static final int SR_ACTION_QUEUE					= 0;
					public static final int SR_ACTION_PAUSE					= 1;
					public static final int SR_ACTION_STOP					= 2;
					public static final int SR_ACTION_ARCHIVE				= 3;
					public static final int SR_ACTION_REMOVE_FROM_LIBRARY	= 4;
					public static final int SR_ACTION_REMOVE_FROM_COMPUTER	= 5;							
					 */
					mapT.put("max-sr-action", rl.getTagMaxShareRatioAction());
				}

				int tagMaxAggregateShareRatio = rl.getTagMaxAggregateShareRatio();
				if (tagMaxAggregateShareRatio > 0) {
					mapT.put("max-aggregate-sr", tagMaxAggregateShareRatio);
					mapT.put("max-aggregate-sr-action",
							rl.getTagMaxAggregateShareRatioAction());
					mapT.put("max-aggregate-sr-prioritize",
							rl.getTagMaxAggregateShareRatioHasPriority());
				}

				if (tagType.getTagType() == TagType.TT_DOWNLOAD_MANUAL) {
					mapT.put("first-priority-seeding", rl.getFirstPrioritySeeding());
				}
			}
		}

		return map;
	}

	private static boolean addIfNotNull(Map<String, Object> map, String key,
			File val) {
		if (val == null) {
			return false;
		}
		map.put(key, val.getAbsolutePath());
		return true;
	}

	public void lookupStart(PluginInterface pi, Map<String, Object> args,
			Map<String, Object> result) {
		Object ids = args.get("ids");

		TagSearchInstance tagSearchInstance = new TagSearchInstance();

		try {
			List<String> listDefaultNetworks = new ArrayList<>();
			for (int i = 0; i < AENetworkClassifier.AT_NETWORKS.length; i++) {

				String nn = AENetworkClassifier.AT_NETWORKS[i];

				String config_name = Connection.BCFG_PREFIX_NETWORK_SELECTION_DEF + nn;
				boolean enabled = COConfigurationManager.getBooleanParameter(
						config_name, false);
				if (enabled) {
					listDefaultNetworks.add(nn);
				}
			}

			com.biglybt.pif.download.DownloadManager dlm = pi.getDownloadManager();
			String[] networks;
			if (ids instanceof List) {
				List idList = (List) ids;
				for (Object id : idList) {
					if (id instanceof String) {
						String hash = (String) id;
						byte[] hashBytes = ByteFormatter.decodeString(hash);
						Download download = dlm.getDownload(hashBytes);

						DownloadManager dm = PluginCoreUtils.unwrap(download);
						if (dm != null) {
							networks = dm.getDownloadState().getNetworks();
						} else {
							networks = listDefaultNetworks.toArray(new String[0]);
						}

						tagSearchInstance.addSearch(hash, hashBytes, networks);
						synchronized (active_tagsearches) {
							active_tagsearches.put(tagSearchInstance.getID(),
									tagSearchInstance);
						}
					}
				}

			}
		} catch (Throwable t) {

		}

		result.put("id", tagSearchInstance.getID());
	}

	public void lookupGetResults(Map<String, Object> args,
			Map<String, Object> result)
			throws IOException {
		String id = (String) args.get("id");

		if (id == null) {
			throw (new IOException("ID missing"));
		}

		synchronized (active_tagsearches) {
			TagSearchInstance search_instance = active_tagsearches.get(id);

			if (search_instance != null) {
				if (search_instance.getResults(result)) {
					active_tagsearches.remove(id);
				}
			} else {
				throw (new IOException("ID not found - already complete?"));
			}
		}
	}

	public void add(Map<String, Object> args, Map<String, Object> result)
			throws IOException, TagException, TextualException {
		set(args, result, true);
	}

	public void set(Map<String, Object> args, Map<String, Object> result,
			boolean addIfNoExists)
			throws TagException, TextualException {
		String name = MapUtils.getMapString(args, FIELD_TAG_NAME, null);
		long tagUID = MapUtils.getMapLong(args, FIELD_TAG_UID, -1);

		if ((name == null || name.isEmpty()) && tagUID < 0) {
			throw (new TextualException("name or uid missing"));
		}

		TagManager tm = TagManagerFactory.getTagManager();

		if (!tm.isEnabled()) {
			throw new TextualException("TagManager isn't enabled");
		}

		int tagTypeID = tagUID == -1 ? TagType.TT_DOWNLOAD_MANUAL
				: (int) (tagUID >> 32);
		TagType tt = tm.getTagType(tagTypeID);

		if (tt == null) {
			throw new TextualException("Invalid Tag UID");
		}

		Tag tag = tagUID != -1 ? tt.getTag((int) (tagUID & 0xFFFF))
				: tt.getTag(name, true);
		boolean needAdd = tag == null;
		if (needAdd) {
			if (addIfNoExists) {
				tag = tt.createTag(name, false);
			} else {
				throw new TextualException("Tag '" + name + "' doesn't exist");
			}
		} else if (name != null) {
			tag.setTagName(name);
		}

		if (tag.canBePublic()) {
			tag.setPublic(MapUtils.getMapBoolean(args, FIELD_TAG_PUBLIC, false));
		}
		tag.setGroup(MapUtils.getMapString(args, FIELD_TAG_GROUP, null));
		String color = MapUtils.getMapString(args, FIELD_TAG_COLOR, null);
		if (color != null) {
			tag.setColor(StaticUtils.htmlColorToRGB(color));
		}

		if (tag.getTagType().hasTagTypeFeature(TagFeature.TF_PROPERTIES)
				&& (tag instanceof TagFeatureProperties)) {
			TagFeatureProperties tfp = (TagFeatureProperties) tag;

			final TagProperty propConstraint = tfp.getProperty(
					TagFeatureProperties.PR_CONSTRAINT);
			if (propConstraint != null) {
				Map mapConstraint = MapUtils.getMapMap(args, "constraint", null);
				if (mapConstraint != null && mapConstraint.size() > 0) {
					String text = MapUtils.getMapString(mapConstraint, "text", "");
					boolean enabled = MapUtils.getMapBoolean(mapConstraint, "enabled",
							true);
					String mode = MapUtils.getMapString(mapConstraint, "mode", null);
					propConstraint.setEnabled(enabled);
					propConstraint.setStringList(new String[] {
						text,
						mode
					});
				}
			}
		}

		if (needAdd) {
			tt.addTag(tag);
		}
		
		result.putAll(buildTagMap(tag, tag.getTagType(), null, true));
	}
}
