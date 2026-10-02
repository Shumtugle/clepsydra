# Releasing

How a version of the game goes out. Every step works from a phone with a
browser; nothing here needs more than the repository's web pages.

## Before

1. Play the build for an evening on more than one phone: a gentle flask, a
   knotty one, one with a clock, one with bubbles, one with a float. Turn the
   phone's dark theme on and off, try each tone and each language, leave the
   game and come back.
2. Raise the version in `AndroidManifest.xml`: `versionCode` must grow with
   every release (a phone refuses an update whose code is not higher),
   `versionName` is what people read.
3. If any English words changed, write the template out again and bring the
   modules up to it: every module must have the same keys as
   `modules/template.txt`, in the same order. Copy the finished modules into
   `res/raw/` as `lang_fi.txt` and `lang_ru.txt`.
4. Write what changed at the top of `CHANGELOG.md`.

## Build

```sh
KEYSTORE=path/to/keystore KSPASS=... SDK=path/to/android.jar sh build.sh clepsydra-1.00
```

The APK appears in `out/`. Sign every release with the same key: Android
installs an update only over an APK signed with the key of the one before.
Keep the key and its password out of the repository.

## Publish

1. Commit everything but `out/` and `build/`.
2. On the repository page: **Releases → Draft a new release**.
3. **Choose a tag** → type `v1.00` → **Create new tag on publish**. The tag
   names the version exactly as `versionName` does, with a `v` before it.
4. Title: `Clepsydra 1.00`. Description: the version's part of
   `CHANGELOG.md`.
5. Attach the APK, named `clepsydra-1.00.apk`.
6. **Publish release**.

## The page

The page lives in `docs/index.html`. To serve it: **Settings → Pages →
Build and deployment → Deploy from a branch**, branch `main`, folder
`/docs`, **Save**. It appears at `https://<owner>.github.io/<repository>/`
a minute or two later. The page links to the latest release, so it needs no
change for a new version unless the game itself changed.
