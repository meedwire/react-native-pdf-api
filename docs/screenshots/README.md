# Screenshots

Capture these from the example app (`example/`) and commit the PNGs here, then
reference them from the root `README.md`.

Suggested shots:

| File                   | What to show                                          |
| ---------------------- | ----------------------------------------------------- |
| `ios-viewer.png`       | A multi-page PDF in the continuous viewer (iOS).      |
| `ios-search.png`       | A search match highlighted/focused (iOS).             |
| `android-viewer.png`   | The same document in the viewer (Android).            |
| `android-search.png`   | A search match highlighted/focused (Android).         |

## How to capture

```sh
# Start Metro from the example
cd example && npm start
```

**iOS**

```sh
cd example/ios && pod install && cd -
npx react-native run-ios --simulator "iPhone 16"
# Capture the current simulator screen:
xcrun simctl io booted screenshot docs/screenshots/ios-viewer.png
```

**Android** (with an emulator booted via `emulator -avd <name>`)

```sh
npx react-native run-android
# Capture the current device/emulator screen:
adb exec-out screencap -p > docs/screenshots/android-viewer.png
```

Type a term (e.g. `simple`) in the search box and tap **Find** before taking the
`*-search.png` shots.
