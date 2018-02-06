#!/bin/bash

zip_plugins=( azupnpav azutp xmwebui aercm )
jar_plugins=( mlDHT )

azupnpav_excludes=( 'com/aelitis/azureus/plugins/upnpmediaserver/ui/swt/*' )
xmwebui_excludes=( 'com/aelitis/azureus/plugins/xmwebui/swt/*' )
aercm_excludes=( 'com/aelitis/plugins/rcmplugin/RelatedContentUISWT*' 'com/aelitis/plugins/rcmplugin/SBC_RCMView*' 'com/aelitis/plugins/rcmplugin/RCM_SubViewHolder*' 'com/aelitis/plugins/rcmplugin/columns/*' )


if [ -z "$1" ]; then
	echo "$0 <client version> [specific plugin only]"
	exit
fi

funcRemoveThingsFromJAR() { # PlugID, Dest
		# remove files in root of jar
		for f in "$2/$1"*.jar; do
			zip -wsq -d "$f" '*' > /dev/null
		done
		
		# remove .java files in jar
		for f in "$2/$1"*.jar; do
			zip -q -d "$f" '*.java' > /dev/null
		done
		
		excludes=( $(eval echo \${${1}_excludes[@]}) )
		if [ -n "$excludes" ]; then
			echo ".. Excludes: $excludes"
			for excludeThis in "${excludes[@]}"; do
				for f in "$2/$1"*.jar; do
					zip -q -d "$f" "$excludeThis"
				done
			done
		fi
}

funcUpdatePlugin() { # Plugin, Dest, DestAssets, ver
	echo "ZIP Plugin: $1; Dest: $2; DestAssets: $3; ver: $4"
	url="http://plugins.biglybt.com/getplugin.php?plugin=$1&type=zip&version=$4&os=a"
	echo "  $url"
	wget -q -O tmp/$1.zip $url
	if [ -f "tmp/$1.zip" ]
	then
		# unzip to DestAssets
		if [ -d $3 ]; then
			rm -rf "$3"
		fi
		mkdir -p "$3"
		unzip -q "tmp/$1.zip" -d "$3"
		
		# move all jar to Dest
		rm -f "$2/"$1*
		mv "$3/"*.jar "$2"
		
		funcRemoveThingsFromJAR $1 $2
	fi
}


funcUpdatePluginJAR() { # Plugin, Dest, DestAssets, ver
	echo "JAR Plugin: $1; Dest: $2; DestAssets: $3; ver: $4"
	# remove old plugin
	rm -f "$2/"$1*
	# wget new plugin jar directly into Dest
	url="http://plugins.biglybt.com/getplugin.php?plugin=$1&type=jar&version=$4&os=a"
	echo "  $url"
	wget -q --content-disposition -P "$2/" $url

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

echo 'Done.  In Android Studio, you will need to Tools->Android->Sync Project with Gradle Files'
exit