# Updrop

An Android app that watches [Farsroid](https://www.farsroid.com) app pages and tells you when a new build is posted.

- Add an app page (paste the link, or Share it from the browser).
- See the version installed on your phone, its CPU type, and the latest version on Farsroid.
- Get a recommended file to download: right CPU, your preferred flavour, and the **same signing key** as your installed copy, so updates install in place and keep your data.
- Updrop checks the signing key of every downloaded file before installing. If it differs from your installed copy, Android would refuse it with "package conflicts with an existing package". Updrop explains why and offers a guided uninstall-then-install.
- Background checks with notifications, and Updrop updates itself from this repo's GitHub Releases.

Black OLED design with Xbox-green accents.

## Install
Download the latest `.apk` from [Releases](https://github.com/Hytyei/Updrop/releases/latest). Android 8.0 or newer.

## Build
JDK 21, then `gradlew assembleRelease`. Release signing reads `keystore.properties` or the `UPDROP_*` environment variables (see `release.ps1`).

Updrop is not affiliated with Farsroid.
