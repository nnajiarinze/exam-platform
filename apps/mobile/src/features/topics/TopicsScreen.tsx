import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { StyleSheet, Text, View } from 'react-native';
import { learningApi } from '../../api/learningApi';
import { friendlyError } from '../../api/errors';
import { useAppStore } from '../../app/store';
import { AppHeader } from '../../components/AppHeader';
import { BottomTabBar } from '../../components/BottomTabBar';
import { Screen } from '../../components/Screen';
import { EmptyState, ErrorState, Loading } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { theme } from '../../theme';
import { TopicList } from './TopicList';
import { useAuth } from '../auth/AuthContext';

export function TopicsScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'Topics'>) {
  const identity = useAppStore((s) => s.learnerIdentity);
  const auth = useAuth();
  const query = useQuery({ queryKey: ['subjects'], queryFn: () => learningApi.subjects(identity), enabled: Boolean(identity) });
  const navigateTab = (tab: 'home' | 'topics' | 'exam' | 'progress' | 'settings') => { if (tab === 'home') navigation.navigate('Home'); else if (tab === 'exam') navigation.navigate('MockExam'); else if (tab === 'progress') navigation.navigate('Progress'); else if(tab==='settings')navigation.navigate('Settings'); };
  return <View style={styles.page}><Screen bottomInset><AppHeader onBack={() => navigation.goBack()} action="profile" onAction={() => navigation.navigate('Profile')} />
    <Text accessibilityRole="header" style={styles.title}>Choose a subject</Text><Text style={styles.subtitle}>Prepare for your exam by selecting a category below.</Text>
    {!identity ? <ErrorState message="No learner identity is configured." actionLabel="Sign out" action={() => void auth.logout()} /> : query.isPending ? <Loading label="Loading topics…" /> : query.isError ? <ErrorState message={friendlyError(query.error)} retry={() => query.refetch()} actionLabel="Sign out" action={() => void auth.logout()} /> : query.data.length === 0 ? <EmptyState message="No published subjects are available yet." /> : <TopicList subjects={query.data} onSelect={(topicId, topicName) => navigation.navigate('PracticeSetup', { mode: 'TOPIC', topicId, topicName })} />}
  </Screen><BottomTabBar active="topics" onNavigate={navigateTab} /></View>;
}
const styles = StyleSheet.create({ page: { backgroundColor: theme.colors.background, flex: 1 }, title: { color: theme.colors.text, ...theme.typography.heading, marginTop: theme.spacing.xs }, subtitle: { color: theme.colors.muted, ...theme.typography.body, marginBottom: theme.spacing.md } });
