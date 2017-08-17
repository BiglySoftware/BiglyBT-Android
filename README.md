# BiglyBT-Android
BiglyBT for Android

This is fork of [Vuze Remote](https://svn.vuze.com/public/android/remote/trunk/VuzeRemoteProject/).

Current status is a bit unstable, and will be fixed up in the next few weeks.

For anyone trying to run from source, there's one key step to ensure the remote part works:
   
>   ```
>   chmod +x updatePlugins.sh
>   ./updatePlugins 1.0.0.1
>   ```
>  
>   This should copy the required plugin JARs and assets into your local repo.  In Android Studio, you may have to re-sync your the /BiglyBT/build.gradle file in order for AS to pick up the JARs
