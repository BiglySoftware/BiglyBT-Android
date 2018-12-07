# BiglyBT-Android

[![API](https://img.shields.io/badge/API-15%2B-blue.svg?style=flat)](https://android-arsenal.com/api?level=15)
[![License](https://img.shields.io/badge/license-GPL2+-blue.svg)](NOTICE)

BiglyBT for Android is an ad-free, fully featured open source bittorrent client and remote control optimized for phones, tablets, Chromebooks, and Android TVs.

You can find our app on Google Play.  Here's a link that maybe Google Search will like :wink: [BiglyBT: Torrent Downloading Client for Android](https://play.google.com/store/apps/details?id=com.biglybt.android.client) 

You can also get it F-Droid:

<a href="https://f-droid.org/packages/com.biglybt.android.client/" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="90"/></a>

This is fork of [Vuze Remote for Android](https://svn.vuze.com/public/android/remote/trunk/VuzeRemoteProject/), which stopped development in early 2017.


## Contributing

### Spread the Word

As a user, the most important thing you can do to help us is to spread the word of BiglyBT. BiglyBT is fairly unknown. Mentioning BiglyBT on your favorite tracker site, blog, wiki, facebook, social media sites, and to friends would help us a lot. Don't get discouraged.  

Other people will have different client preferences than you, and they will often feel attacked when someone talks about a client that isn't theirs.  Try to keep discussions positive and informative!  We want to co-exist with all clients, since all clients can help share.

### Translations

Our translations for BiglyBT for Android are typically behind compared to BiglyBT core translations.  Our Android project only has a couple of dozen languages, compared to the 40+ in BiglyBT core, and most of them are terribly incomplete.

Preferably, we'd love it if your native language wasn't English, however, anyone fluent in another language is very much appreciated.

To join in on the translations, please visit our [BiglyBT-Android CrowdIn](https://crowdin.com/project/biglybt-android) project.  Translations are open to anyone with a CrowdIn account.  If you would like to translate into a language that isn't listed, please let us know by creating a GitHub Issue and we will add it!

### Code Style

We use the plugin [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) to format the code, with the scheme in [BiglyBT/PreferencesJavaCodeStyleFormatter.xml](BiglyBT/PreferencesJavaCodeStyleFormatter.xml)


## Building

### Initial Steps

Using Android Studio's Import Project from Version control will **not** work, since the feature doesn't handle git submodules. (Note: this might be fixed in IntelliJ 2018.3 via [Bug 64024](https://youtrack.jetbrains.com/issue/IDEA-64024), however the latest AS 3.3 uses 2018.2.2).

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

5. `File`->`Sync Project with Gradle Files`. This resolves the **Error: Please select Android SDK** error, as well as ensuring all jars in the libs/ folder are processed.

### Updating Source

No special steps are needed to update the android source.  A simple `VCS`->`Update Project...` will suffice.

You can occasionaly check for submodule updates with the `git submodule update` in the Terminal window

To get fresh plugin jars, you can run `./updatePlugins.sh <version>` with the latest beta version number of BiglyBT which is listed at the top of https://github.com/BiglySoftware/BiglyBT/blob/master/ChangeLog.txt

