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

DSTDIR=${SRCDIR}_B${NEXTVER}
rm -rf "${DSTDIR}"

echo Copying ${SRCDIR} to ${DSTDIR}

cp -r "${SRCDIR}" "${DSTDIR}"
sed -i .bk -e "s/DEBUG = true/DEBUG = false/g" "${DSTDIR}/src/com/vuze/android/remote/AndroidUtils.java"
rm -r "${DSTDIR}/src/com/vuze/android/remote/AndroidUtils.java.bk"
rm -r "${DSTDIR}/bin/"*
rm -r "${DSTDIR}/gen/"*
find "${DSTDIR}" -name '.svn' -exec rm -rf {} \;
find "${DSTDIR}/assets" -name '.[a-z]*' -exec rm -rf {} \;
find "${DSTDIR}/assets" -name 'toolbar-*' -exec rm -rf {} \;
find "${DSTDIR}/assets" -name 'toolbar_butt*' -exec rm -rf {} \;
find "${DSTDIR}/assets" -name '*.scss' -exec rm -rf {} \;
find "${DSTDIR}/assets" -name 'easy*' -exec rm -rf {} \;
find "${DSTDIR}/src" -name '*.txt' -exec rm -rf {} \;
rm -r "${DSTDIR}/assets/transmission/web/javascript/jquery/jquery-1.10.2.js"
rm -r "${DSTDIR}/assets/transmission/web/javascript/jquery/jquery-ui-1.10.3.custom.js"
rm -r "${DSTDIR}/assets/transmission/web/style/jqueryui/jquery-ui-1.10.3.css"
rm -r "${DSTDIR}/assets/transmission/web/style/transmission/images/buttons/torrent"*.png
rm -r "${DSTDIR}/assets/transmission/web/LICENSE"
rm -rf "${DSTDIR}/assets/transmission/web/images"
rm -rf "${DSTDIR}/assets/transmission/web/style/transmission/images/graphics"
rm -rf "${DSTDIR}/assets/transmission/web/style/transmission/images/filter_icon.png"
rm -rf "${DSTDIR}/assets/transmission/web/style/transmission/images/settings.png"
rm -rf "${DSTDIR}/assets/transmission/web/style/transmission/images/logo.png"
rm -rf "${DSTDIR}/assets/transmission/web/style/transmission/images/buttons/cancel.png"
cd "${DSTDIR}"
android update project --name VuzeAndroidRemote --path .
ant clean
ant ${BUILDTYPE}
cp -f "${DSTDIR}/bin/VuzeAndroidRemote-release.apk" ../VuzeAndroidRemote-${NEXTVER}.apk
tar -czf "${DSTDIR}.tar.gz" .
