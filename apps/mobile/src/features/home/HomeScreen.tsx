import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { StyleSheet, Text, View } from 'react-native';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { AppHeader } from '../../components/AppHeader';
import { BottomTabBar } from '../../components/BottomTabBar';
import { Screen } from '../../components/Screen';
import { Button, Card, Icon, ProgressBar } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { theme } from '../../theme';

export function HomeScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'Home'>) {
  const sessionId = useAppStore((s) => s.currentSessionId);
  const identity = useAppStore((s) => s.learnerIdentity);
  const progress = useQuery({ queryKey: ['progress'], queryFn: () => learningApi.progress(identity), enabled: Boolean(identity) });
  const answered = progress.data?.reduce((sum, item) => sum + item.questionsAnswered, 0) ?? 0;
  const correct = progress.data?.reduce((sum, item) => sum + item.correctAnswers, 0) ?? 0;
  const accuracy = answered > 0 ? Math.round((correct / answered) * 100) : 0;
  const navigateTab = (tab: 'home' | 'topics' | 'exam' | 'progress') => { if (tab === 'topics') navigation.navigate('Topics'); else if (tab === 'exam') navigation.navigate('MockExam'); else if (tab === 'progress') navigation.navigate('Progress'); };

  return <View style={styles.page}><Screen bottomInset>
    <AppHeader action="profile" onAction={() => navigation.navigate('Settings')} />
    <View style={styles.hero}><View style={styles.heroOrb} /><Text style={styles.heroTitle}>Welcome to Svea Study</Text><Text style={styles.heroBody}>Your path to Swedish citizenship starts here.</Text></View>
    <Text style={styles.sectionLabel}>STUDY NOW</Text>
    {sessionId && <Card><Text style={styles.resumeTitle}>Continue studying</Text><Text style={styles.muted}>Your current practice session is ready.</Text><Button label="Continue" icon={<Icon name="play" color={theme.colors.onPrimary} size={18} />} onPress={() => navigation.navigate('Question', { sessionId })} /></Card>}
    <Button label="Start practice" icon={<Icon name="play" color={theme.colors.onPrimary} size={18} />} onPress={() => navigation.navigate('Topics')} />
    <Button label="Mixed practice" variant="secondary" icon={<Icon name="shuffle" size={20} />} onPress={() => navigation.navigate('PracticeSetup', { mode: 'MIXED' })} />
    <Text style={styles.sectionLabel}>YOUR PROFILE</Text>
    <View style={styles.profileRow}>
      <View style={styles.profileCard}><Card><View style={[styles.iconCircle, styles.yellow]}><Icon name="progress" size={22} /></View><Text style={styles.cardTitle}>Your progress</Text><Text style={styles.muted}>View your statistics</Text><Button label="View" variant="secondary" onPress={() => navigation.navigate('Progress')} /></Card></View>
      <View style={styles.profileCard}><Card><View style={[styles.iconCircle, styles.blue]}><Icon name="settings" size={24} /></View><Text style={styles.cardTitle}>Settings</Text><Text style={styles.muted}>Personalise the app</Text><Button label="Open" variant="secondary" onPress={() => navigation.navigate('Settings')} /></Card></View>
    </View>
    {answered > 0 && <View style={styles.insight}><View style={styles.insightCopy}><Text style={styles.insightTitle}>You are making progress</Text><Text style={styles.insightText}>{accuracy}% accuracy across {answered} answered {answered === 1 ? 'question' : 'questions'}.</Text><ProgressBar value={accuracy} accessibilityLabel="Overall accuracy" /></View><View style={styles.sparkle}><Icon name="trophy" size={24} color={theme.colors.success} /></View></View>}
  </Screen><BottomTabBar active="home" onNavigate={navigateTab} /></View>;
}

const styles = StyleSheet.create({
  page: { backgroundColor: theme.colors.background, flex: 1 },
  hero: { backgroundColor: theme.colors.primary, borderRadius: theme.radii.xl, minHeight: 190, overflow: 'hidden', padding: theme.spacing.md, justifyContent: 'flex-end' },
  heroOrb: { backgroundColor: theme.colors.primaryContainer, borderRadius: 160, height: 260, opacity: 0.55, position: 'absolute', right: -80, top: -100, width: 260 },
  heroTitle: { color: theme.colors.onPrimary, ...theme.typography.heading, maxWidth: 360 }, heroBody: { color: '#EAF1FF', ...theme.typography.bodyLarge, marginTop: 4 },
  sectionLabel: { color: theme.colors.text, ...theme.typography.label, letterSpacing: 1.2, marginTop: theme.spacing.sm },
  resumeTitle: { color: theme.colors.primary, ...theme.typography.label }, muted: { color: theme.colors.muted, ...theme.typography.caption },
  profileRow: { flexDirection: 'row', gap: theme.spacing.sm },
  profileCard: { flex: 1 },
  iconCircle: { alignItems: 'center', borderRadius: theme.radii.full, height: 48, justifyContent: 'center', width: 48 }, yellow: { backgroundColor: theme.colors.accent }, blue: { backgroundColor: theme.colors.surfaceHigh },
  cardTitle: { color: theme.colors.text, ...theme.typography.label, marginTop: 4 },
  insight: { alignItems: 'center', backgroundColor: theme.colors.surface, borderColor: theme.colors.divider, borderRadius: theme.radii.xl, borderWidth: 1, flexDirection: 'row', gap: theme.spacing.sm, padding: theme.spacing.md },
  insightCopy: { flex: 1, gap: 6 }, insightTitle: { color: theme.colors.success, ...theme.typography.label }, insightText: { color: theme.colors.success, ...theme.typography.body },
  sparkle: { alignItems: 'center', backgroundColor: theme.colors.surface, borderRadius: theme.radii.full, height: 52, justifyContent: 'center', width: 52, ...theme.shadows.card },
});
