import Constants from 'expo-constants';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { AppHeader } from '../../components/AppHeader';
import { BottomTabBar, type Tab } from '../../components/BottomTabBar';
import { Screen } from '../../components/Screen';
import { Button, ErrorState, Loading, StatusBadge } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { theme } from '../../theme';
import { useAuth } from '../auth/AuthContext';
import { Divider, SettingsRow, SettingsSection } from './SettingsUi';

export function SettingsScreen({navigation}:NativeStackScreenProps<RootStackParamList,'Settings'>){
  const auth=useAuth();const identity=auth.claims?.sub??useAppStore(s=>s.learnerIdentity);
  const profile=useQuery({queryKey:['learner-profile',identity],queryFn:()=>learningApi.profile(identity),enabled:Boolean(identity)});
  const settings=useQuery({queryKey:['learner-settings',identity],queryFn:()=>learningApi.settings(identity),enabled:Boolean(identity)});
  const navigate=(tab:Tab)=>{if(tab==='home')navigation.navigate('Home');else if(tab==='topics')navigation.navigate('Topics');else if(tab==='progress')navigation.navigate('Progress');else if(tab==='exam')navigation.navigate('MockExam');};
  const unavailable=(name:string)=>Alert.alert(name,`${name} is not configured for this development build.`);
  if(profile.isPending)return <Screen><Loading label="Loading settings…"/></Screen>;
  if(profile.isError)return <Screen><ErrorState message="We could not load your account." retry={()=>profile.refetch()} actionLabel="Sign out" action={()=>void auth.logout()}/></Screen>;
  const data=profile.data;const initials=(data.displayName||'Learner').split(/\s+/).slice(0,2).map(v=>v[0]?.toUpperCase()).join('');
  return <View style={styles.page}><Screen bottomInset><AppHeader onBack={()=>navigation.navigate('Home')} action="help" onAction={()=>unavailable('Help Center')}/>
    <View style={styles.hero}><Pressable accessibilityRole="button" accessibilityLabel="Edit profile" onPress={()=>navigation.navigate('EditProfile')} style={styles.avatar}><Text style={styles.initials}>{initials}</Text><View style={styles.edit}><Text style={styles.editText}>✎</Text></View></Pressable><Text style={styles.name}>{data.displayName}</Text><Text style={styles.email}>{data.email??'Email unavailable'}</Text><View style={styles.badges}><StatusBadge label={data.emailVerified?'Email verified':'Verification required'} tone={data.emailVerified?'success':'neutral'}/><StatusBadge label={data.accountStatus} tone={data.accountStatus==='ACTIVE'?'success':'error'}/></View></View>
    <SettingsSection title="Account"><SettingsRow icon="person" label="Edit Profile" onPress={()=>navigation.navigate('EditProfile')}/><Divider/><SettingsRow icon="lock" label="Change Password" onPress={()=>navigation.navigate('ChangePassword')}/><Divider/><SettingsRow icon="delete" label="Delete Account" destructive onPress={()=>navigation.navigate('DeleteAccount')}/></SettingsSection>
    <SettingsSection title="Learning"><SettingsRow icon="notification" label="Notification Preferences" detail={settings.data?.studyReminderEnabled?`Reminder at ${settings.data.preferredReminderTime.slice(0,5)}`:'Reminders off'} onPress={()=>navigation.navigate('NotificationPreferences')}/><Divider/><SettingsRow icon="flag" label="Study Goal" detail={settings.data?`${settings.data.dailyQuestionGoal} questions daily`:'Loading goal…'} onPress={()=>navigation.navigate('StudyGoals')}/></SettingsSection>
    <SettingsSection title="Support"><SettingsRow icon="help" label="Help Center" onPress={()=>unavailable('Help Center')}/><Divider/><SettingsRow icon="privacy" label="Privacy and Legal" onPress={()=>navigation.navigate('PrivacyLegal')}/></SettingsSection>
    <Button label="Sign Out" variant="destructive" icon={<Text style={styles.signOutIcon}>↪</Text>} onPress={()=>void auth.logout()}/><Text style={styles.version}>Version {Constants.expoConfig?.version??'development'}</Text>
  </Screen><BottomTabBar active="settings" onNavigate={navigate}/></View>;
}

export function ProfileScreen(props:NativeStackScreenProps<RootStackParamList,'Profile'>){return <SettingsScreen {...(props as unknown as NativeStackScreenProps<RootStackParamList,'Settings'>)}/>;}

const styles=StyleSheet.create({page:{backgroundColor:theme.colors.background,flex:1},hero:{alignItems:'center',backgroundColor:theme.colors.surface,borderRadius:theme.radii.xl,gap:6,padding:theme.spacing.md,...theme.shadows.card},avatar:{alignItems:'center',backgroundColor:theme.colors.surfaceHigh,borderColor:theme.colors.surfaceHigh,borderRadius:52,borderWidth:4,height:104,justifyContent:'center',marginBottom:theme.spacing.xs,width:104},initials:{color:theme.colors.primary,fontSize:34,fontWeight:'700'},edit:{alignItems:'center',backgroundColor:theme.colors.primary,borderRadius:20,bottom:-2,height:38,justifyContent:'center',position:'absolute',right:-4,width:38},editText:{color:theme.colors.onPrimary,fontSize:20},name:{color:theme.colors.text,...theme.typography.heading},email:{color:theme.colors.text,...theme.typography.body,textAlign:'center'},badges:{alignItems:'center',flexDirection:'row',gap:theme.spacing.xs,marginTop:theme.spacing.xs},version:{color:theme.colors.muted,...theme.typography.caption,textAlign:'center'},signOutIcon:{color:theme.colors.error,fontSize:22}});
