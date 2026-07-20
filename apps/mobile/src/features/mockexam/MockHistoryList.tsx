import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { MockExamHistoryItem } from '../../api/generated/types.gen';
import { Card } from '../../components/ui';
import { theme } from '../../theme/theme';

export function MockHistoryList({ attempts, onSelect }: { attempts: MockExamHistoryItem[]; onSelect: (attemptId: string) => void }) {
  if (attempts.length === 0) return <Text>No completed mock examinations yet.</Text>;
  return <View style={styles.list}>{attempts.map((attempt) => <Pressable accessibilityRole="button" key={attempt.attemptId} onPress={() => onSelect(attempt.attemptId)}><Card><Text style={styles.name}>{attempt.name}</Text><Text>{new Date(attempt.startedAt).toLocaleDateString()}</Text><Text>{attempt.score}/{attempt.totalQuestions} · {attempt.percentage}% · {attempt.passed ? 'Passed' : 'Not passed'}</Text><Text style={styles.muted}>{Math.floor(attempt.durationSeconds / 60)}m {attempt.durationSeconds % 60}s</Text></Card></Pressable>)}</View>;
}
const styles = StyleSheet.create({ list: { gap: 12 }, name: { fontSize: 17, fontWeight: '700' }, muted: { color: theme.colors.muted } });
