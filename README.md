# BiglyBT-Android
BiglyBT for Android

This is fork of [Vuze Remote for Android](https://svn.vuze.com/public/android/remote/trunk/VuzeRemoteProject/).


## Status

Current status is a bit unstable, and will be fixed up in the next few weeks.


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
>   ./updatePlugins 1.0.0.1
>   ```
>  
>   This should copy the required plugin JARs and assets into your local source tree.  In Android Studio, you may have to re-sync your the /BiglyBT/build.gradle file in order for AS to pick up the JARs


## Code Style

We use the plugin [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) to format the code, with the scheme in [BiglyBT/PreferencesJavaCodeStyleFormatter.xml](BiglyBT/PreferencesJavaCodeStyleFormatter.xml)
