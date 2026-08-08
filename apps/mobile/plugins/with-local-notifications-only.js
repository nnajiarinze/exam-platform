const { createRunOncePlugin } = require('expo/config-plugins');
const {
  withNotificationsAndroid,
} = require('expo-notifications/plugin/build/withNotificationsAndroid');
const notificationsPackage = require('expo-notifications/package.json');

/**
 * Medbo schedules local study reminders and does not register for APNs.
 * expo-notifications adds aps-environment for both local and remote use cases.
 * Retain its Android configuration, but skip its push-only iOS configuration.
 */
const withLocalNotificationsOnly = (config, props = {}) =>
  withNotificationsAndroid(config, props);

module.exports = createRunOncePlugin(
  withLocalNotificationsOnly,
  notificationsPackage.name,
  notificationsPackage.version,
);
