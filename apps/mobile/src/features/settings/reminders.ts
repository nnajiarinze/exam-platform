import { Linking, Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import * as SecureStore from 'expo-secure-store';

const key = (learnerId: string) => `svea-study.reminder.${learnerId}`;

export type ReminderPermission = 'granted' | 'denied' | 'undetermined';

export async function reminderPermission(): Promise<ReminderPermission> {
  const result = await Notifications.getPermissionsAsync();
  if (result.granted) return 'granted';
  return result.canAskAgain ? 'undetermined' : 'denied';
}

export async function requestReminderPermission(): Promise<ReminderPermission> {
  const result = await Notifications.requestPermissionsAsync();
  if (result.granted) return 'granted';
  return result.canAskAgain ? 'undetermined' : 'denied';
}

export async function cancelStudyReminder(learnerId: string) {
  const identifier = await SecureStore.getItemAsync(key(learnerId));
  if (identifier) await Notifications.cancelScheduledNotificationAsync(identifier).catch(() => undefined);
  await SecureStore.deleteItemAsync(key(learnerId));
}

export async function scheduleStudyReminder(learnerId: string, time: string) {
  await cancelStudyReminder(learnerId);
  if (Platform.OS === 'android') await Notifications.setNotificationChannelAsync('study-reminders', { name: 'Study reminders', importance: Notifications.AndroidImportance.DEFAULT });
  const [hour, minute] = time.split(':').map(Number);
  const identifier = await Notifications.scheduleNotificationAsync({
    content: { title: 'Ready for a short study session?', body: 'Continue preparing for your Swedish citizenship knowledge exam.', data: { category: 'study-reminder' } },
    trigger: { type: Notifications.SchedulableTriggerInputTypes.DAILY, hour, minute, channelId: Platform.OS === 'android' ? 'study-reminders' : undefined },
  });
  await SecureStore.setItemAsync(key(learnerId), identifier);
}

export async function applyReminderPreference(learnerId: string, enabled: boolean, time: string) {
  if (!enabled) return cancelStudyReminder(learnerId);
  if (await reminderPermission() !== 'granted') return;
  return scheduleStudyReminder(learnerId, time);
}

export function openNotificationSettings() { return Linking.openSettings(); }
