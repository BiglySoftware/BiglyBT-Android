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

package com.biglybt.update;

import com.biglybt.android.client.AndroidUtils;
import com.biglybt.android.util.FileUtils;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.FileUtil;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildAutoStrings
{
	private static final Pattern PAT_PARAM_ALPHA = Pattern.compile(
			"\\{([^0-9].+?)\\}");

	public static String toEscapeAndroid(String str) {

		StringBuilder sb = new StringBuilder();
		char[] charArr = str.toCharArray();
		char lastChar = '\0';
		boolean isNewLine = false;
		for (char c : charArr) {
			if (isNewLine) {
				if (c == ' ') {
					continue;
				}
				sb.append('\n');
				isNewLine = false;
			}
			
			if (c == '\n') {
				sb.append("\\n");
				isNewLine = true;
			} else if (c == '\t') {
				sb.append("\\t");
			} else if (c == '@' || c == '?' || c == '\'' || c == '"') {
				sb.append('\\');
				sb.append(c);
			} else if (c == '<') {
				sb.append("&lt;");
			} else if (c == '&') {
				sb.append("&amp;");
			} else if (lastChar == '.' && c == ' ') {
				isNewLine = true;
			} else if (c < 128 && c != '\\') {
				sb.append(c);
			} else {
				sb.append(c);
			}
			lastChar = c;
		}
		return sb.toString();
	}

	public static String expandValue(String value, Properties properties,
			Properties defaultProps) {
		String oValue = value;
		// Replace {*} with a lookup of *
		if (value != null && value.indexOf('}') > 0) {
			Matcher matcher = PAT_PARAM_ALPHA.matcher(value);
			while (matcher.find()) {
				String key = matcher.group(1);
				try {
					String text = properties.getProperty(key);
					if (text == null) {
						text = defaultProps.getProperty(key);
						if (text == null) {
							System.err.println("null text for key " + key + "; " + AndroidUtils.getCompressedStackTrace());
							return null;
						}
					}

					value = expandValue(
							value.replace("{" + key + "}", Matcher.quoteReplacement(text)),
							properties, defaultProps);
				} catch (MissingResourceException e) {
					// ignore error
				} catch (Throwable t) {
					System.err.println(t.toString() + " for value=" + value + "; was=" + oValue);
					t.printStackTrace();
				}
			}
		}
		return value;
	}

	public static void main(String[] args)
			throws IOException {
		System.setProperty("SKIP_SETRB", "1");

		process("BiglyBT/src/coreFlavor/res");
		process("BiglyBT/src/main/res.lang");
	}

	private static void process(String keysSubDir)
			throws IOException {
		System.out.println(keysSubDir);
		System.out.println("----");
		File dirRoot = new File("").getAbsoluteFile();
		System.out.println("root: " + dirRoot);
		File dirKeys = new File(dirRoot, keysSubDir);
		System.out.println("KeyDir: " + dirKeys);
		if (!dirKeys.isDirectory()) {
			System.err.println("no " + dirKeys);
			return;
		}
		String coreSrcDir = System.getProperty("core.src.dir",
				new File(dirRoot.getParentFile(),
						"BiglyBT/core/src/com/biglybt/internat").getAbsolutePath());
		File dirFullMB = new File(coreSrcDir);
		System.out.println("Src : " + dirFullMB);
		if (!dirFullMB.isDirectory()) {
			System.err.println("Invalid -Dcore.src.dir : no " + dirFullMB);
			return;
		}

		File fileCoreStringKeys = new File(dirKeys, "AutoStringKeys.properties");
		if (!fileCoreStringKeys.isFile()) {
			System.err.println(fileCoreStringKeys.toString() + " doesn't exist");
			return;
		}
		File fileFullDefault = new File(dirFullMB, "MessagesBundle.properties");

		FileInputStream fisFullDefault = new FileInputStream(fileFullDefault);
		Properties defaultFullProperties = new Properties();
		defaultFullProperties.load(fisFullDefault);
		fisFullDefault.close();
		System.out.println(
				"Keys in " + fileFullDefault + " : " + defaultFullProperties.size());

		File rcmDir = new File(dirRoot.getParentFile(),
				"Plugins/aercm/com/aelitis/plugins/rcmplugin/internat");
		FileInputStream fisRCM = new FileInputStream(
				new File(rcmDir, "Messages.properties"));
		defaultFullProperties.load(fisRCM);
		fisRCM.close();
		
		defaultFullProperties.putAll(MessageText.CONSTANTS);

		// Maintain order by reading keys manually instead of via Properties.load
		String s = FileUtil.readFileAsString(fileCoreStringKeys, -1);
		String[] lines = s.split("\r?\n");

		File[] files = dirFullMB.listFiles(
				(dir, filename) -> filename.endsWith(".properties"));

		File[] resDirs = dirKeys.listFiles(pathname -> pathname.isDirectory()
				&& new File(pathname, "auto-strings.xml").exists());
		System.out.print("rm ");
		for (File res : resDirs) {
			File f = new File(res, "auto-strings.xml");
			if (f.exists()) {
				System.out.print(res.getName() + "(" + f.delete() + "), ");
			}
		}
		System.out.println("done");

		Map<String, String> mapCustomLangNames = new HashMap<>();
		mapCustomLangNames.put("MessagesBundle_pt_PT.properties", "values-pt-rPT");
		mapCustomLangNames.put("MessagesBundle_pt_BR.properties", "values-pt-rBR");
		mapCustomLangNames.put("MessagesBundle_zh_CN.properties", "values-zh-rCN");
		mapCustomLangNames.put("MessagesBundle_zh_TW.properties", "values-zh-rTW");
		mapCustomLangNames.put("MessagesBundle_es_VE.properties", "values-es-rVE");
		mapCustomLangNames.put("MessagesBundle_sr_Latn.properties",
				"values-b+sr+latn");
		mapCustomLangNames.put("MessagesBundle_vls_BE.properties", "values-nl-rBE");
		mapCustomLangNames.put("MessagesBundle_in_id.properties", "values-in");

		for (File file : files) {

			FileInputStream fisFullMB = new FileInputStream(file);
			Properties fullCurrentProperties = new Properties();
			fullCurrentProperties.load(fisFullMB);
			fisFullMB.close();

			String fileName = file.getName();
			boolean isCurrentFileDefaultLang = !fileName.contains("_");

			int i = fileName.indexOf("_");
			File pluginFile = new File(rcmDir,
					"Messages" + (i > 0 ? fileName.substring(i) : ".properties"));
			if (pluginFile.exists()) {
				FileInputStream fisPlug = new FileInputStream(pluginFile);
				fullCurrentProperties.load(fisPlug);
				fisPlug.close();
			}

			String[] split = isCurrentFileDefaultLang ? new String[] {
				"",
				"en",
				"",
				""
			} : fileName.split("[_\\.]");
			if (split[1].equals("vls")) {
				split[1] = "nl";
			}
			Locale locale = new Locale(split[1], split.length == 3 ? "" : split[2]);
			String valuesDir;
			String newname = mapCustomLangNames.get(fileName);
			if (newname != null) {
				valuesDir = newname;
			} else if (isCurrentFileDefaultLang) {
				valuesDir = "values";
			} else {
				valuesDir = "values-" + locale.getLanguage();
			}

			File fileOutXML = new File(dirKeys, valuesDir + "/auto-strings.xml");
			System.out.println("Process " + fileName + " for "
					+ locale.getDisplayName(locale) + " to " + fileOutXML);
			if (fileOutXML.exists()) {
				System.err.println("ALREADY EXIsts");
				continue;
			}
			fileOutXML.getParentFile().mkdirs();

			System.setProperty("line.separator", "\n");
			BufferedWriter bwXML = new BufferedWriter(new FileWriter(fileOutXML));
			bwXML.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
			bwXML.newLine();
			bwXML.write("<!-- " + locale.getDisplayName() + " / "
					+ locale.getDisplayName(locale)
					+ " generated by BuildAutoStrings -->");
			bwXML.newLine();
			bwXML.write("<resources>");
			bwXML.newLine();

			String contextLine = null;

			int numEntriesWrote = 0;

			for (String line : lines) {
				if (line.isEmpty()) {
					bwXML.newLine();
					continue;
				}

				if (line.startsWith("#")) {
					if (!line.startsWith("##")) {
						contextLine = "\t<!-- " + line.substring(1) + " -->";
					}
					continue;
				}

				String[] key_val = line.split("=", 2);
				if (key_val.length != 2) {
					//System.err.println("Invalid: " + line);
					continue;
				}

				String keyOrig = key_val[0];
				String keyXML = key_val[1].isEmpty() ? keyOrig.replaceAll("\\.", "_")
						: key_val[1];
				String[] valSplit = keyXML.split(",");
				if (valSplit.length > 1) {
					keyXML = valSplit[0];
				}
				String fullCurrentVal = fullCurrentProperties.getProperty(keyOrig);
				if (fullCurrentVal == null) {
					// no key in the full messagebundle for this language, maybe the
					// default language's value expands to another key?
					String defaultFullVal = defaultFullProperties.getProperty(keyOrig);
					if (defaultFullVal == null || !defaultFullVal.contains("}")) {
						if (contextLine != null) {
							bwXML.write(contextLine);
							bwXML.newLine();
						}
						bwXML.write("\t<!-- No entry for " + keyOrig + " -->");
						bwXML.newLine();
						contextLine = null;
					} else {
						String expandedValue = expandValue(defaultFullVal,
								fullCurrentProperties, defaultFullProperties);
						if (expandedValue == null) {
							if (contextLine != null) {
								bwXML.write(contextLine);
								bwXML.newLine();
							}
							bwXML.write("<!-- No expansion for " + line + " -->");
							bwXML.newLine();
							numEntriesWrote++;
						} else {
							if (valSplit.length > 1) {
								int splitPos = 1;
								while (splitPos < valSplit.length) {
									String regex = valSplit[splitPos];
									splitPos++;
									String regReplace = splitPos < valSplit.length ? valSplit[splitPos] : "";
									regReplace = regReplace.replace("\\n", "\n");
									splitPos++;
									expandedValue = expandedValue.replaceAll(regex, regReplace);
								}
							}
							if (contextLine != null) {
								bwXML.write(contextLine);
								bwXML.newLine();
								contextLine = null;
							}
							bwXML.write("\t<string name=\"" + keyXML + "\"");
							if (isCurrentFileDefaultLang) {
								bwXML.write(" translatable=\"false\"");
							}
							bwXML.write(">" + toEscapeAndroid(expandedValue) + "</string>");
							bwXML.newLine();
							numEntriesWrote++;
						}
					}
				} else {
					String expandedValue = expandValue(fullCurrentVal,
							fullCurrentProperties, defaultFullProperties);
					if (expandedValue == null) {
						if (contextLine != null) {
							bwXML.write(contextLine);
							bwXML.newLine();
						}
						bwXML.write("<!-- No expansion for " + line + " -->");
						bwXML.newLine();
						numEntriesWrote++;
					} else {
						if (valSplit.length > 1) {
							int splitPos = 1;
							while (splitPos < valSplit.length) {
								String regex = valSplit[splitPos];
								splitPos++;
								String regReplace = splitPos < valSplit.length ? valSplit[splitPos] : "";
								regReplace = regReplace.replace("\\n", "\n");
								splitPos++;
								expandedValue = expandedValue.replaceAll(regex, regReplace);
							}
						}
						if (contextLine != null) {
							bwXML.write(contextLine);
							bwXML.newLine();
							contextLine = null;
						}
						bwXML.write("\t<string name=\"" + keyXML + "\"");
						if (isCurrentFileDefaultLang) {
							bwXML.write(" translatable=\"false\"");
						}
						bwXML.write(">" + toEscapeAndroid(expandedValue) + "</string>");
						bwXML.newLine();
						numEntriesWrote++;
					}
				}
			}
			bwXML.write("</resources>\n");
			bwXML.close();

			System.err.flush();

			System.out.print(numEntriesWrote + " entries wrote");
			if (numEntriesWrote == 0) {
				fileOutXML.delete();
				System.out.print(" -> delete file");
//			} else if (fileOutXML.getParentFile().getName().equals("values-in")) {
//				// hack so we support old and new Indonesian codes (id, in)
//				FileUtil.copyFile(fileOutXML, new File(new File(dirKeys, "values-id"), fileOutXML.getName()));
			}
			System.out.println();
			System.out.println();
		}

		File[] delDirs = dirKeys.listFiles(
				pathname -> pathname.isDirectory() && pathname.listFiles().length == 0);
		System.out.print("rm ");
		for (File delDir : delDirs) {
			System.out.print(delDir.getName() + "(" + delDir.delete() + "), ");
		}
		System.out.println("done");
	}

}
