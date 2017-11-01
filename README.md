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

For anyone trying to run from source, there's one key step to ensure the remote part works:
   
>   ```
>   chmod +x updatePlugins.sh
>   ./updatePlugins 1.0.2.1
>   ```
>  
>   This should copy the required plugin JARs and assets into your local source tree.  In Android Studio, you may have to re-sync your the /BiglyBT/build.gradle file in order for AS to pick up the JARs


## Code Style

We use the plugin [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) to format the code, with the scheme in [BiglyBT/PreferencesJavaCodeStyleFormatter.xml](BiglyBT/PreferencesJavaCodeStyleFormatter.xml)
