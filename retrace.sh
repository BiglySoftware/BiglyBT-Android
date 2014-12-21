#!/bin/bash
if [ "$1" == "" ] ; then
	echo $0 '<Release Number> <class>[.method[:line]]'
	exit
fi
adb=`which adb`
androidsdk=$(dirname ${adb})/..
proguardbin=$androidsdk/tools/proguard/bin
paths=(""
	"com.vuze.android.remote."
	"com.vuze.android.remote.activity."
	"com.vuze.android.remote.dialog."
	"com.vuze.android.remote.fragment."
	"com.vuze.android.remote.rpc."
	"android.support.v4.app." 
)
# Replace all : with .
class=`echo $2 | sed -e "s/\:\([^0-9]\)/.\1/g"`
buildno=$1

echo Searching Build $buildno for $class

scan() {
	gotResult=false
	for i in "${paths[@]}"
	do
	result=`echo ${i}${class} | $proguardbin/retrace.sh -regex "$1" ../old/VuzeAndroidRemote-${buildno}/VuzeRemoteProject/bin/proguard/mapping.txt`
	if [ "$result" != "${i}${class}" ] 
	  then
	  	if [ "$gotResult" == false ] ; then
	  		echo
			echo $2
			gotResult=true
		fi
		echo $result
	fi
	done
}



echo VuzeAndroidRemote_B$1

if [[ $2 =~ .*:[0-9]+$ ]]
then
	scan "%c\.%m:%l" "Methods (With Lines)"
	exit
else
	scan "%c" "Classes"
	scan "%c\.%m" "Methods"
	scan "%c\.%m:%l" "Methods (With Lines)"
	scan "%c\.%f" "Fields"
fi
