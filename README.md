# BiglyBT-Android
BiglyBT for Android is an ad-free, fully featured open source bittorrent client and remote control optimized for phones, tablets, Chromebooks, and Android TVs.

You can find our app on Google Play.  Here's a link that maybe Google Search will like :wink: [BiglyBT: Torrent Downloading Client for Android](https://play.google.com/store/apps/details?id=com.biglybt.android.client) 

You can also get it F-Droid:

<a href="https://f-droid.org/packages/com.biglybt.android.client/" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="90"/></a>

This is fork of [Vuze Remote for Android](https://svn.vuze.com/public/android/remote/trunk/VuzeRemoteProject/).


## Status

Released!


## What's New since Vuze Remote for Android 2.6.1

* Collapsable Grouping headers for torrents, such as 
  * "Active", "Inactive", "Active Today", "LastActive" when sorting by active
  * xx% to xx% complete when sorting by complete
  * General file groups of "1", "many", "hundreds", "thousands" when sorting by file count
* Fixed launching of files from local Vuze Core on Android N


## Building

Using Android Studio's Import Project from Version control will **not** work, since the feature doesn't handle git submodules.

1. From the command line, run the following:

    ```
    git clone https://github.com/BiglySoftware/BiglyBT-Android.git
    cd BiglyBT-Android
    git submodule update --init --recursive
    chmod +x updatePlugins.sh
    ./updatePlugins.sh 1.5.0.1
    ```

    `git submodule update --init --recursive` will pull in Android-Toggle-Switch. If you previously tried to import the project with Android Studio, you may have to `rm -rf Android-Toggle-Switch` before this command.

    `updatePlugins` will copy the required plugin JARs and assets into your local source tree.

2. Use the standard `File`->`Open` in AS and select the BiglyBT-Android folder.

3. Turn off `Configure on Demand` in AS preferences.

4. (Optional) Choose the correct build variant to compile with using `Build`->`Select Build Variant`.  The most tested variant is `coreFlavorGoogleFlavorDebug`.


## Code Style

We use the plugin [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) to format the code, with the scheme in [BiglyBT/PreferencesJavaCodeStyleFormatter.xml](BiglyBT/PreferencesJavaCodeStyleFormatter.xml)
