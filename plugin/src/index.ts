import {
  withInfoPlist,
  withAndroidManifest,
  AndroidConfig,
  ConfigPlugin,
} from "expo/config-plugins";

export type Parameters = {
  android: {
    projectId: string;
  };
  ios?: {
    iCloudContainerIdentifiers?: string[];
    iCloudServices?: string[];
  };
};

const withInitialConfigs: ConfigPlugin<Parameters> = (config, { android, ios }) => {
  config = withInfoPlist(config, (config) => {
    if (!config.ios) {
      config.ios = {};
    }

    const iCloudContainerIdentifiers = ios?.iCloudContainerIdentifiers ?? [];
    const iCloudServices = ios?.iCloudServices ??
      (iCloudContainerIdentifiers.length > 0 ? ["CloudDocuments"] : undefined);

    config.ios.entitlements = {
      ...config.ios.entitlements,
      "com.apple.developer.game-center": true,
      ...(iCloudContainerIdentifiers.length > 0 && {
        "com.apple.developer.icloud-container-identifiers": iCloudContainerIdentifiers,
      }),
      ...(iCloudServices && iCloudServices.length > 0 && {
        "com.apple.developer.icloud-services": iCloudServices,
      }),
    };

    return config;
  });

  config = withAndroidManifest(config, (config) => {
    const mainApplication = AndroidConfig.Manifest.getMainApplicationOrThrow(
      config.modResults
    );

    AndroidConfig.Manifest.addMetaDataItemToMainApplication(
      mainApplication,
      "com.google.android.gms.games.APP_ID",
      android.projectId
    );

    return config;
  });

  return config;
};

export default withInitialConfigs;
