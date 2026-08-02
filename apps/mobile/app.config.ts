import appJson from './app.json';

const selectedEnvironment = process.env.EXPO_PUBLIC_APP_ENV || 'LOCAL';
const buildKind = process.env.EXPO_PUBLIC_BUILD_KIND || 'development';
const gateway = process.env.EXPO_PUBLIC_API_BASE_URL || 'https://api.46-224-221-7.sslip.io';

if (!['LOCAL', 'HOSTED'].includes(selectedEnvironment)) {
  throw new Error(`Unknown EXPO_PUBLIC_APP_ENV "${selectedEnvironment}". Expected LOCAL or HOSTED.`);
}
if (buildKind === 'production' && selectedEnvironment === 'LOCAL') {
  throw new Error('Production mobile builds cannot use the LOCAL backend environment.');
}
if (buildKind === 'production' && !gateway.startsWith('https://')) {
  throw new Error('Production mobile builds require an HTTPS hosted API base URL.');
}

export default {
  ...appJson,
  expo: {
    ...appJson.expo,
    ios: {
      ...appJson.expo.ios,
      infoPlist: {
        ...appJson.expo.ios.infoPlist,
        NSAppTransportSecurity: {
          NSAllowsArbitraryLoads: false,
          NSAllowsLocalNetworking: true,
        },
      },
    },
  },
};
