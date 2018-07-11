#!/bin/bash
# I think this one uses the plugin source

zip_plugins=( azupnpav azutp xmwebui aercm )
jar_plugins=( mlDHT )

azupnpav_excludes=( 'com/aelitis/azureus/plugins/upnpmediaserver/ui/swt/*' )
xmwebui_excludes=( 'com/aelitis/azureus/plugins/xmwebui/swt/*' )
aercm_excludes=( 'com/aelitis/plugins/rcmplugin/RelatedContentUISWT*' 'com/aelitis/plugins/rcmplugin/SBC_RCMView*' 'com/aelitis/plugins/rcmplugin/RCM_SubViewHolder*' 'com/aelitis/plugins/rcmplugin/columns/*' )


if [ -z "$1" ]; then
	echo "$0 <client version> [specific plugin only]"
	exit
fi

rm -rf "/Volumes/Workspace/workspace-as/BiglyBT-Android/core/plugins"
mkdir "/Volumes/Workspace/workspace-as/BiglyBT-Android/core/plugins"

funcRemoveThingsFromJAR() { # PlugID, Dest
	for f in "$2/$1"*.jar; do
		unzip -qo "$f" -d "/Volumes/Workspace/workspace-as/BiglyBT-Android/core/plugins/"
		find "/Volumes/Workspace/workspace-as/BiglyBT-Android/core/plugins" -name "*.class" -type f -delete
		rm -r "/Volumes/Workspace/workspace-as/BiglyBT-Android/core/plugins/META-INF"
		# remove files in root of jar
		rm /Volumes/Workspace/workspace-as/BiglyBT-Android/core/plugins/*
	done
	
	excludes=( $(eval echo \${${1}_excludes[@]}) )
	if [ -n "$excludes" ]; then
		echo ".. Excludes: $excludes"
		for excludeThis in "${excludes[@]}"; do
			for f in "$2/$1"*.jar; do
				eval "rm /Volumes/Workspace/workspace-as/BiglyBT-Android/core/plugins/$excludeThis"
			done
		done
	fi

	for f in "$2/$1"*.jar; do
		rm "$f" 
	done
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
		rm -f "$2/"$1*
		mv "$3/"*.jar "$2"
		
		funcRemoveThingsFromJAR $1 $2
	fi
}


funcUpdatePluginJAR() { # Plugin, Dest, DestAssets, ver
	echo "Plugin: $1; Dest: $2; DestAssets: $3; ver: $4"
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