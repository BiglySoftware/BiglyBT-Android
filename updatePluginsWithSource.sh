#!/bin/bash
# I think this one uses the plugin source

if [ -d "core" ]; then
	DEST_PLUGIN_DIR=$PWD/core/plugins
	echo "Destination for Plugins Source = ${DEST_PLUGIN_DIR}"
else
	echo "No 'core' folder.  Not in BiglyBT-Android folder, or haven't ran copyCoreToAndroid.sh"
	exit
fi


if [ -z "$1" ]; then
	echo "$0 <client version> [specific plugin only]"
	exit
fi


zip_plugins=( azupnpav azutp xmwebui aercm )
#jar_plugins=( mlDHT )

azupnpav_excludes=( 'com/aelitis/azureus/plugins/upnpmediaserver/ui/swt/*' )
xmwebui_excludes=( 'com/aelitis/azureus/plugins/xmwebui/swt/*' )
aercm_excludes=( 'com/aelitis/plugins/rcmplugin/RelatedContentUISWT*' 'com/aelitis/plugins/rcmplugin/SBC_RCMView*' 'com/aelitis/plugins/rcmplugin/RCM_SubViewHolder*' 'com/aelitis/plugins/rcmplugin/RCM_SubViewEventListener*' 'com/aelitis/plugins/rcmplugin/columns/*' )
mlDHT_excludes=( 'lbms/plugins/mldht/azureus/gui/*' )


rm -rf "${DEST_PLUGIN_DIR}"
mkdir "${DEST_PLUGIN_DIR}"

funcRemoveThingsFromJAR() { # PlugID, Dest
	for f in "$2/$1"*.jar; do
		echo "unzip $f"
		unzip -qo "$f" -d "${DEST_PLUGIN_DIR}/"
		find "${DEST_PLUGIN_DIR}" -name "*.class" -type f -delete
		rm -r "${DEST_PLUGIN_DIR}/META-INF"
		# remove files in root of jar
		find ${DEST_PLUGIN_DIR} -maxdepth 1 -type f -delete
		#rm ${DEST_PLUGIN_DIR}/*
	done
	
	excludes=( $(eval echo \${${1}_excludes[@]}) )
	if [ -n "$excludes" ]; then
		echo ".. Excludes: $excludes"
		for excludeThis in "${excludes[@]}"; do
			for f in "$2/$1"*.jar; do
				eval "rm ${DEST_PLUGIN_DIR}/$excludeThis"
			done
		done
	fi

	for f in "$2/$1"*.jar; do
		rm "$f" 
	done
}

funcRemoveThingsFromDir() { # PlugID
	find "${DEST_PLUGIN_DIR}" -name "*.class" -type f -delete
	rm -r "${DEST_PLUGIN_DIR}/META-INF"
	# remove files in root of jar
	find ${DEST_PLUGIN_DIR} -maxdepth 1 -type f -delete
	
	excludes=( $(eval echo \${${1}_excludes[@]}) )
	if [ -n "$excludes" ]; then
		echo ".. Excludes: $excludes"
		for excludeThis in "${excludes[@]}"; do
			eval "rm ${DEST_PLUGIN_DIR}/$excludeThis"
		done
	fi
}

funcUpdatePlugin() { # Plugin, Dest, DestAssets, ver
	echo "Plugin: $1; Dest: $2; DestAssets: $3; ver: $4"
	wget -q -O tmp/$1.zip "http://plugins.biglybt.com/getplugin.php?plugin=$1&type=zip&version=$4&os=a"
	if [ -f "tmp/$1.zip" ]
	then
		# unzip to DestAssets
		if [ -d $3 ]; then
			rm -rf "$3"
		fi
		mkdir -p "$3"
		unzip -q "tmp/$1.zip" -d "$3"
		
		# move all jar to Dest
		echo "rm -f $2/$1*"
		rm -f "$2/"$1*
		mv "$3/"*.jar "$2"

		funcRemoveThingsFromJAR $1 $2
	fi
	echo "--"
}


funcUpdatePluginJAR() { # Plugin, Dest, DestAssets, ver
	echo "JarPlugin: $1; Dest: $2; DestAssets: $3; ver: $4"
	# remove old plugin
	rm -f "$2/"$1*
	# wget new plugin jar directly into Dest
	wget -q --content-disposition -P "$2/" "http://plugins.biglybt.com/getplugin.php?plugin=$1&type=zip&version=$4&os=a"

	# copy plugin.properties to DestAssets/<plugin>/
	if [ -d $3 ]; then
		rm -rf "$3"
	fi
	mkdir -p "$3"
	unzip -q -d "$3/" "$2/$1*.jar" plugin.properties

	funcRemoveThingsFromJAR $1 $2
}

mkdir tmp

for plugin in "${zip_plugins[@]}"
do
	if [ -z "$2" ] || [ "$2" == "$plugin" ]; then
		funcUpdatePlugin $plugin "BiglyBT/libs" "BiglyBT/src/coreFlavor/assets/plugins/${plugin}" $1
	fi
done

# mlDHT JAR doesn't have source.. :(
rm -rf tmp
mkdir tmp

git clone https://github.com/BiglySoftware/BiglyBT-plugin-mlDHT.git tmp
git -C tmp reset --hard 1dd254c2f0118f9505ebca36efd8bff01b39a951
rm -rf tmp/.git
mkdir "BiglyBT/src/coreFlavor/assets/plugins/mlDHT"
mv tmp/plugin.properties "BiglyBT/src/coreFlavor/assets/plugins/mlDHT/"
cp -r tmp/ "${DEST_PLUGIN_DIR}/"
funcRemoveThingsFromDir "mlDHT"

rm -rf tmp
mkdir tmp



for plugin in "${jar_plugins[@]}"
do
	if [ -z "$2" ] || [ "$2" == "$plugin" ]; then
		funcUpdatePluginJAR $plugin "BiglyBT/libs" "BiglyBT/src/coreFlavor/assets/plugins/${plugin}" $1
	fi
done

echo Zipping up all plugins into assets dir
rm "BiglyBT/src/coreFlavor/assets/plugins.zip"
( cd "BiglyBT/src/coreFlavor/assets" ; zip -q -r "plugins.zip" "plugins" -x "*.DS_Store" )
rm -rf "BiglyBT/src/coreFlavor/assets/plugins"

rm -rf tmp

echo 'Done.  In Android Studio, you will need to File->Sync Project with Gradle Files'
exit