import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { learningApi } from '../../api/learningApi';
import { friendlyError } from '../../api/errors';
import { useAppStore } from '../../app/store';
import { AppHeader } from '../../components/AppHeader';
import { BottomTabBar } from '../../components/BottomTabBar';
import { Screen } from '../../components/Screen';
import { EmptyState, ErrorState, Icon, Loading, ProgressBar } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { theme } from '../../theme';

export function StudySubjectsScreen({navigation}:NativeStackScreenProps<RootStackParamList,'StudySubjects'>){
  const identity=useAppStore(s=>s.learnerIdentity);
  const query=useQuery({queryKey:['study-subjects',identity],queryFn:()=>learningApi.studySubjects(identity),enabled:Boolean(identity)});
  const tabs=(tab:'home'|'topics'|'exam'|'progress'|'settings')=>{if(tab==='home')navigation.navigate('Home');else if(tab==='exam')navigation.navigate('MockExam');else if(tab==='progress')navigation.navigate('Progress');};
  return <View style={styles.page}><Screen bottomInset><AppHeader onBack={()=>navigation.goBack()} action="profile" onAction={()=>navigation.navigate('Profile')}/><Text accessibilityRole="header" style={styles.title}>Study</Text><Text style={styles.subtitle}>Learn the key topics before practicing.</Text>
    {query.isPending?<Loading label="Loading subjects…"/>:query.isError?<ErrorState message={friendlyError(query.error)} retry={()=>query.refetch()}/>:query.data.length===0?<EmptyState message="No published lessons are available yet."/>:<View style={styles.list}>{query.data.map((subject,index)=>{const percent=subject.topicCount?subject.completedTopicCount*100/subject.topicCount:0;return <Pressable accessibilityRole="button" accessibilityLabel={`Open ${subject.title}`} key={subject.subjectId} style={styles.card} onPress={()=>navigation.navigate('StudyTopics',{subjectId:subject.subjectId,subjectTitle:subject.title})}><View style={styles.icon}><Icon name={index%2?'progress':'topics'} size={25}/></View><Text accessibilityRole="header" style={styles.cardTitle}>{subject.title}</Text><Text style={styles.meta}>{subject.topicCount} {subject.topicCount===1?'topic':'topics'} · {subject.completedTopicCount} complete</Text><ProgressBar value={percent} accessibilityLabel={`${Math.round(percent)} percent complete`}/></Pressable>})}</View>}
  </Screen><BottomTabBar active="topics" onNavigate={tabs}/></View>
}
const styles=StyleSheet.create({page:{backgroundColor:theme.colors.background,flex:1},title:{color:theme.colors.text,...theme.typography.heading},subtitle:{color:theme.colors.muted,...theme.typography.body,marginBottom:theme.spacing.md},list:{gap:theme.spacing.sm},card:{backgroundColor:theme.colors.surface,borderColor:theme.colors.divider,borderRadius:theme.radii.xl,borderWidth:1,gap:theme.spacing.xs,padding:theme.spacing.md,...theme.shadows.card},icon:{alignItems:'center',backgroundColor:theme.colors.surfaceHigh,borderRadius:theme.radii.lg,height:52,justifyContent:'center',width:52},cardTitle:{color:theme.colors.text,...theme.typography.subheading},meta:{color:theme.colors.muted,...theme.typography.body}});
