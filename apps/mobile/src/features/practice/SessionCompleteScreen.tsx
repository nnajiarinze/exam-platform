import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { StyleSheet, Text, View } from 'react-native';
import { useAppStore } from '../../app/store';
import { AppHeader } from '../../components/AppHeader';
import { Screen } from '../../components/Screen';
import { Button, Icon, ProgressBar, StatusBadge } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { theme } from '../../theme';
import { sessionAccuracy } from './summary';

function elapsedLabel(startedAt?: string) {
  if (!startedAt) return undefined;
  const seconds = Math.max(0, Math.round((Date.now() - new Date(startedAt).getTime()) / 1000));
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
}

export function SessionCompleteScreen({ navigation, route }: NativeStackScreenProps<RootStackParamList, 'SessionComplete'>) {
  const correct = useAppStore((s) => s.correctAnswers); const startedAt = useAppStore((s) => s.sessionStartedAt); const setSession = useAppStore((s) => s.setSession);
  const accuracy = sessionAccuracy(correct, route.params.total); const duration = elapsedLabel(startedAt); const missed = Math.max(0, route.params.total - correct);
  const finish = () => { setSession(undefined); navigation.popTo('Home'); };
  return <Screen><AppHeader onBack={finish} action="profile" onAction={() => navigation.navigate('Settings')} />
    <View style={styles.celebration}><View style={styles.trophy}><Icon name="trophy" size={42} color={theme.colors.onAccent} /></View><Text accessibilityRole="header" style={styles.title}>Practice complete!</Text><Text style={styles.subtitle}>Well done!</Text></View>
    <View style={styles.results}><View style={styles.resultHeader}><Text style={styles.overline}>RESULT SUMMARY</Text><StatusBadge label="Complete" tone="success" /></View>
      <View style={styles.resultBody}><View style={styles.accuracy}><Text style={styles.metricLabel}>Accuracy</Text><Text style={styles.accuracyValue}>{accuracy}%</Text><ProgressBar value={accuracy} accessibilityLabel="Session accuracy" /></View>
        <View style={styles.metrics}><View style={styles.metricCard}><Icon name="check" color={theme.colors.success} /><Text style={styles.metricLabel}>Correct answers</Text><Text style={styles.metricValue}>{correct}/{route.params.total}</Text></View>{duration && <View style={styles.metricCard}><Icon name="exam" /><Text style={styles.metricLabel}>Time</Text><Text style={styles.metricValue}>{duration}</Text></View>}</View>
        <View style={styles.encouragement}><Icon name="info" color={theme.colors.onAccent} /><Text style={styles.encouragementText}>{missed === 0 ? 'Excellent work. You answered every question correctly.' : `Keep going. Review the ${missed} ${missed === 1 ? 'question' : 'questions'} you missed as you continue studying.`}</Text></View>
      </View>
    </View>
    <Button label="Finish" onPress={finish} />
  </Screen>;
}
const styles = StyleSheet.create({
  celebration: { alignItems: 'center', gap: theme.spacing.xs, paddingVertical: theme.spacing.md }, trophy: { alignItems: 'center', backgroundColor: theme.colors.accent, borderRadius: theme.radii.full, height: 96, justifyContent: 'center', width: 96, ...theme.shadows.card }, title: { color: theme.colors.text, ...theme.typography.heading, marginTop: theme.spacing.xs }, subtitle: { color: theme.colors.primary, ...theme.typography.bodyLarge, fontWeight: '700' },
  results: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderRadius: theme.radii.xl, borderWidth: 1, overflow: 'hidden', ...theme.shadows.card }, resultHeader: { alignItems: 'center', borderBottomColor: theme.colors.divider, borderBottomWidth: 1, flexDirection: 'row', justifyContent: 'space-between', padding: theme.spacing.md }, overline: { color: theme.colors.muted, ...theme.typography.label, letterSpacing: 1 }, resultBody: { gap: theme.spacing.sm, padding: theme.spacing.md },
  accuracy: { alignItems: 'center', backgroundColor: theme.colors.surfaceContainer, borderRadius: theme.radii.xl, gap: theme.spacing.xs, padding: theme.spacing.md }, accuracyValue: { color: theme.colors.primary, ...theme.typography.display }, metricLabel: { color: theme.colors.muted, ...theme.typography.caption },
  metrics: { flexDirection: 'row', gap: theme.spacing.sm }, metricCard: { backgroundColor: theme.colors.surfaceLow, borderRadius: theme.radii.xl, flex: 1, gap: theme.spacing.xs, minHeight: 132, padding: theme.spacing.sm }, metricValue: { color: theme.colors.text, fontSize: 24, lineHeight: 32, fontWeight: '700' },
  encouragement: { alignItems: 'center', backgroundColor: theme.colors.surfaceHigh, borderLeftColor: theme.colors.accent, borderLeftWidth: 4, borderRadius: theme.radii.lg, flexDirection: 'row', gap: theme.spacing.sm, padding: theme.spacing.sm }, encouragementText: { color: theme.colors.text, flex: 1, ...theme.typography.label },
});
