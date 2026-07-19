import { StyleSheet, Text, View } from 'react-native';
import type { TopicProgress } from '../../api/generated/types.gen';
import { Card } from '../../components/ui';
import { theme } from '../../theme/theme';

export function ProgressList({ progress, topicNames = {} }: { progress: TopicProgress[]; topicNames?: Record<string, string> }) {
  if (progress.length === 0) return <Text>No practice progress yet.</Text>;
  return <View style={styles.list}>{progress.map((item) => <Card key={item.topicId}><Text accessibilityRole="header" style={styles.topic}>{topicNames[item.topicId] ?? item.topicId}</Text><Text>Accuracy: {item.accuracyPercentage}%</Text><Text>Questions answered: {item.questionsAnswered}</Text><Text style={styles.muted}>Last practised: {new Date(item.lastPractisedAt).toLocaleDateString()}</Text></Card>)}</View>;
}
const styles = StyleSheet.create({ list: { gap: 12 }, topic: { fontSize: 18, fontWeight: '700' }, muted: { color: theme.colors.muted } });
