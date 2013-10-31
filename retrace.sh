#!/bin/bash
androidsdk="`which adb`/../.."
proguardbin=$androidsdk/tools/proguard/bin
echo VuzeAndroidRemote_B$1
result=`echo $2 | $proguardbin/retrace.sh -regex "%c" ../VuzeAndroidRemote_B$1/bin/proguard/mapping.txt`
if [ "$result" != "$2" ] 
  then
	echo Classes
	echo $result
fi
result=`echo $2 | $proguardbin/retrace.sh -regex "%c\.%m" ../VuzeAndroidRemote_B$1/bin/proguard/mapping.txt`
if [ "$result" != "$2" ] 
  then
	echo Methods
	echo $result
fi
result=`echo $2 | $proguardbin/retrace.sh -regex "%c\.%m:%l" ../VuzeAndroidRemote_B$1/bin/proguard/mapping.txt`
if [ "$result" != "$2" ] 
  then
	echo "Methods (With Lines)"
	echo $result
fi
result=`echo $2 | $proguardbin/retrace.sh -regex "%c\.%f" ../VuzeAndroidRemote_B$1/bin/proguard/mapping.txt`
if [ "$result" != "$2" ] 
  then
	echo Fields
	echo $result
fi
