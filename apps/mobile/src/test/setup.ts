process.env.EXPO_PUBLIC_API_BASE_URL ??= 'https://api.example.test';
process.env.EXPO_PUBLIC_APP_ENV ??= 'HOSTED';

jest.mock('@react-native-async-storage/async-storage', () => require('@react-native-async-storage/async-storage/jest/async-storage-mock'));
jest.mock('expo-notifications',()=>({
  getPermissionsAsync:jest.fn(),requestPermissionsAsync:jest.fn(),cancelScheduledNotificationAsync:jest.fn(),
  scheduleNotificationAsync:jest.fn(),setNotificationChannelAsync:jest.fn(),AndroidImportance:{DEFAULT:3},
  SchedulableTriggerInputTypes:{DAILY:'daily'},
}));
