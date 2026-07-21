import * as Notifications from 'expo-notifications';
import * as SecureStore from 'expo-secure-store';
import { applyReminderPreference, reminderPermission, scheduleStudyReminder } from './reminders';

const mockNotifications=Notifications as jest.Mocked<typeof Notifications>;
const mockStored=new Map<string,string>();
jest.spyOn(SecureStore,'getItemAsync').mockImplementation((key:string)=>Promise.resolve(mockStored.get(key)??null));
jest.spyOn(SecureStore,'setItemAsync').mockImplementation((key:string,value:string)=>{mockStored.set(key,value);return Promise.resolve()});
jest.spyOn(SecureStore,'deleteItemAsync').mockImplementation((key:string)=>{mockStored.delete(key);return Promise.resolve()});

beforeEach(()=>{jest.clearAllMocks();mockStored.clear();mockNotifications.scheduleNotificationAsync.mockResolvedValue('new-id');mockNotifications.cancelScheduledNotificationAsync.mockResolvedValue();mockNotifications.setNotificationChannelAsync.mockResolvedValue(null);});
it('reports permission accurately',async()=>{mockNotifications.getPermissionsAsync.mockResolvedValue({granted:false,canAskAgain:false,status:'denied',expires:'never'} as never);await expect(reminderPermission()).resolves.toBe('denied');});
it('replaces an existing learner reminder without duplicates',async()=>{mockStored.set('svea-study.reminder.learner-a','old-id');await scheduleStudyReminder('learner-a','19:30:00');expect(mockNotifications.cancelScheduledNotificationAsync).toHaveBeenCalledWith('old-id');expect(mockNotifications.scheduleNotificationAsync).toHaveBeenCalledTimes(1);expect(mockStored.get('svea-study.reminder.learner-a')).toBe('new-id');});
it('cancels instead of scheduling when reminders are disabled',async()=>{mockStored.set('svea-study.reminder.learner-a','old-id');await applyReminderPreference('learner-a',false,'19:30:00');expect(mockNotifications.cancelScheduledNotificationAsync).toHaveBeenCalledWith('old-id');expect(mockNotifications.scheduleNotificationAsync).not.toHaveBeenCalled();});
