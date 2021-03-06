# BiglyBT-Android

[![API](https://img.shields.io/badge/API-15%2B-blue.svg?style=flat)](https://android-arsenal.com/api?level=15)
![License](https://img.shields.io/badge/license-GPL2+-blue.svg?style=flat)

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

In Android Studio, we use the plugin [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) to format the code, with the scheme in [app/PreferencesJavaCodeStyleFormatter.xml](app/PreferencesJavaCodeStyleFormatter.xml)


## Building

### Initial Steps

Due to a long git history for the core source (2003->present), ensure you have at least 400MB available.

1. Import into Android Studio as New Project

2. (Optional) Choose the correct build variant to compile with using `Build`->`Select Build Variant`.  The most usable variant is `coreFlavorFossFlavorDebug`.

### Migrating source from pre-1.3.1.0

#### What's Changed

Prior to 1.3.1.0, BiglyBT Core library was included as a jar, and a shell script was required to grab the plugin .jar files. Starting with 1.3.1.0, core and plugins are included as git submodules.

In the new structure, the Android specific code has moved from `BiglyBT\` to the standard `app\` folder.  

#### Manual Source Migration Steps

If you pulled prior to 1310, this section is for you.

1. Updating project from VCS will likely result in the new submodules not being initialized (the directories `core`, `BiglyBT-plugin-*`, `mldht` will be empty).  To fix this, run in the Terminal window (with path at the root of the project):

   `git submodule update --init`
   
   git should reply with a checkout of 6 submodules


1. `File`->`Sync Projects with Gradle Files` will properly setup the plugin and core module source paths.

1. You can remove the `BiglyBT` folder in the project root.  After the sync, it will not be marked as a Module by Android Studio, and will contain mostly empty directories, along with plugin jars that are no longer used or referenced.

   Be sure to look at your git local changed first, in case you have files that you still want.

1. Check `Settings`->`Other Settings`->`Eclipse Code Formatter`, and ensure the config file path is correct.  It's been moved from `/BiglyBT` to `/app`, but VCS may have prevented it from updating. 

1. Restart Android Studio.  If you use the IntelliJ plugin 'Awesome Console', file links may not work until you restart.

### Updating Source

No special steps are needed to update the android source.  A simple `VCS`->`Update Project...` will suffice.

You can occasionaly check for submodule updates with the `git submodule update` in the Terminal window (Newer Android Studio versions may do this automatically with `Update Project...`)

