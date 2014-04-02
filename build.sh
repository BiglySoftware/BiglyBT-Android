#!/bin/bash
if [ "$1" == "" ]; then
	BUILDTYPE=debug
elif [ "$1" == "releaseagain" ]; then
	BUILDTYPE=release
else
	BUILDTYPE=$1
fi

SRCDIR=`pwd`

ver=$(sed -n 's/.*android:versionCode="\([^"+]*\)".*/\1/p' ${SRCDIR}/AndroidManifest.xml)
if [ "$1" == "releaseagain" ]; then
	NEXTVER=$ver
else
	NEXTVER=$(($ver +1))
	sed -i .bk -e "s/android:versionCode=\"${ver}\"/android:versionCode=\"${NEXTVER}\"/g" ${SRCDIR}/AndroidManifest.xml 
	sed -i .bk -e "s/Alpha ${ver}/Alpha ${NEXTVER}/g" ${SRCDIR}/AndroidManifest.xml 
	rm ${SRCDIR}/AndroidManifest.xml.bk
fi

DSTDIR=${SRCDIR}-${NEXTVER}
DSTDIR_APP=${DSTDIR}/VuzeRemoteProject
rm -rf "${DSTDIR}"

echo Copying ${SRCDIR} to ${DSTDIR_APP}
mkdir ${DSTDIR}
cp -r "${SRCDIR}" "${DSTDIR_APP}"
cp -r "${SRCDIR}/../android-pull-to-refresh" "${DSTDIR}"
cp -r "${SRCDIR}/../PagerSlidingTabStrip" "${DSTDIR}"
cp -r "${SRCDIR}/../appcompat" "${DSTDIR}"
sed -i .bk -e "s/false/true/g" "${DSTDIR_APP}/res/values/analytics.xml"
sed -i .bk -e "s/DEBUG = true/DEBUG = false/g" "${DSTDIR_APP}/src/com/vuze/android/remote/AndroidUtils.java"
rm -r "${DSTDIR_APP}/src/com/vuze/android/remote/AndroidUtils.java.bk"
rm -r "${DSTDIR_APP}/res/values/analytics.xml.bk"
rm -r "${DSTDIR_APP}/src/com/aelitis/azureus/util/JSONUtilsGSON.java"
rm -r "${DSTDIR_APP}/src/com/aelitis/azureus/util/ObjectTypeAdapterLong.java"
find "${DSTDIR_APP}" -name '.svn' -exec rm -rf {} \;
find "${DSTDIR_APP}/src" -name '*.txt' -exec rm -rf {} \;
echo Updating Projects
cd "${DSTDIR}/android-pull-to-refresh"
android update project --path .
cd "${DSTDIR}/PagerSlidingTabStrip"
android update project --path .
cd "${DSTDIR}/appcompat"
android update project --path .
cd "${DSTDIR_APP}"
android update project --name VuzeAndroidRemote --path .
echo Clean
ant clean > /dev/null
echo Build
ant ${BUILDTYPE}
cp -f "${DSTDIR_APP}/bin/VuzeAndroidRemote-release.apk" ${SRCDIR}/../builds/VuzeAndroidRemote-${NEXTVER}.apk
cd ${DSTDIR}
tar -czf "${DSTDIR}.tar.gz" *
mv "${DSTDIR}.tar.gz" ${SRCDIR}/../builds