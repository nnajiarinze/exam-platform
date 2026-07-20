import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { MockExamNavigationItem } from '../../api/generated/types.gen';
import { theme } from '../../theme/theme';

export function QuestionNavigator({ questions, current, onSelect }: { questions: MockExamNavigationItem[]; current: number; onSelect: (sequence: number) => void }) {
  return <View accessibilityLabel="Question navigator" style={styles.grid}>{questions.map((item) => <Pressable accessibilityRole="button" accessibilityLabel={`Question ${item.sequenceNumber}${item.answered ? ', answered' : ', unanswered'}${item.flagged ? ', flagged' : ''}`} key={item.attemptQuestionId} onPress={() => onSelect(item.sequenceNumber)} style={[styles.item, item.answered && styles.answered, item.flagged && styles.flagged, current === item.sequenceNumber && styles.current]}><Text>{item.sequenceNumber}</Text></Pressable>)}</View>;
}
const styles = StyleSheet.create({ grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 }, item: { alignItems: 'center', backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderRadius: 8, borderWidth: 1, height: 40, justifyContent: 'center', width: 40 }, answered: { backgroundColor: theme.colors.successBackground }, flagged: { borderColor: '#a66b00', borderWidth: 2 }, current: { borderColor: theme.colors.primary, borderWidth: 3 } });
