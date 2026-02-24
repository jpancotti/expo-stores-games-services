# expo-stores-games-services

Module to manage Google Play Games Services and Game Center Services

# API documentation

- [Documentation for the latest stable release](https://docs.expo.dev/versions/latest/sdk/stores-games-services/)
- [Documentation for the main branch](https://docs.expo.dev/versions/unversioned/sdk/stores-games-services/)

# Installation in managed Expo projects

For [managed](https://docs.expo.dev/archive/managed-vs-bare/) Expo projects, please follow the installation instructions in the [API documentation for the latest stable release](#api-documentation). If you follow the link and there is no documentation available then this library is not yet usable within managed projects &mdash; it is likely to be included in an upcoming Expo SDK release.

# Installation in bare React Native projects

For bare React Native projects, you must ensure that you have [installed and configured the `expo` package](https://docs.expo.dev/bare/installing-expo-modules/) before continuing.

### Add the package to your npm dependencies

```
npm install expo-stores-games-services
```

### Configure for Android


No additional setup necessary.


### Configure for iOS

Run `npx pod-install` after installing the npm package.

If you use saved games on iOS (`saveGameData`, `fetchSavedGames`, etc.), enable iCloud entitlements with a container ID, for example:

```json
[
  "expo-stores-games-services",
  {
    "android": { "projectId": "1234567890" },
    "ios": {
      "iCloudContainerIdentifiers": ["iCloud.com.example.app"],
      "iCloudServices": ["CloudDocuments"]
    }
  }
]
```

# Saved games API

- `saveGameData(dataBase64, name)`
- `fetchSavedGames()`
- `loadGameData(name)`
- `deleteSavedGames(name)`

`dataBase64` is a base64-encoded string for cross-platform compatibility.

# Contributing

Contributions are very welcome! Please refer to guidelines described in the [contributing guide]( https://github.com/expo/expo#contributing).
