import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Subject } from '../../api/generated/types.gen';
import { Icon } from '../../components/ui';
import { theme } from '../../theme';

export function TopicList({ subjects, onSelect }: { subjects: Subject[]; onSelect: (topicId: string, topicName: string) => void }) {
  return <View style={styles.list}>{subjects.map((subject, subjectIndex) => <View key={subject.id} style={styles.subjectGroup}>
    <View style={styles.subjectCard}><View style={styles.subjectIcon}><Icon name={subjectIndex % 2 === 0 ? 'topics' : 'progress'} size={28} /></View><Text accessibilityRole="header" style={styles.subject}>{subject.name}</Text><Text style={styles.count}>{subject.topics.length} {subject.topics.length === 1 ? 'topic' : 'topics'}</Text></View>
    {subject.topics.map((topic, topicIndex) => <Pressable accessibilityRole="button" accessibilityLabel={`${topic.name}. Start topic practice`} key={topic.id} onPress={() => onSelect(topic.id, topic.name)} style={({ pressed }) => [styles.topic, pressed && styles.pressed]}>
      <View style={[styles.topicIcon, topicIndex % 2 === 1 && styles.topicIconGreen]}><Icon name={topicIndex % 2 === 0 ? 'topics' : 'progress'} size={22} color={topicIndex % 2 === 0 ? theme.colors.onAccent : theme.colors.success} /></View>
      <Text style={styles.topicName}>{topic.name}</Text>{topic.description && <Text style={styles.description}>{topic.description}</Text>}
      <View style={styles.topicFooter}><Text style={styles.action}>Start practice</Text><Icon name="arrow" size={24} /></View>
    </Pressable>)}
  </View>)}</View>;
}
const styles = StyleSheet.create({
  list: { gap: theme.spacing.md }, subjectGroup: { gap: theme.spacing.sm },
  subjectCard: { backgroundColor: theme.colors.surface, borderColor: theme.colors.divider, borderRadius: theme.radii.xl, borderWidth: 1, gap: theme.spacing.xs, padding: theme.spacing.md, ...theme.shadows.card },
  subjectIcon: { alignItems: 'center', backgroundColor: theme.colors.surfaceHigh, borderRadius: theme.radii.lg, height: 58, justifyContent: 'center', marginBottom: 4, width: 58 },
  subject: { color: theme.colors.text, ...theme.typography.subheading }, count: { color: theme.colors.muted, ...theme.typography.body },
  topic: { backgroundColor: theme.colors.surface, borderColor: theme.colors.divider, borderRadius: theme.radii.xl, borderWidth: 1, gap: theme.spacing.xs, padding: theme.spacing.sm, ...theme.shadows.card }, pressed: { borderColor: theme.colors.primary, opacity: 0.85 },
  topicIcon: { alignItems: 'center', backgroundColor: '#FFF7D8', borderRadius: theme.radii.lg, height: 48, justifyContent: 'center', width: 48 }, topicIconGreen: { backgroundColor: theme.colors.successBackground },
  topicName: { color: theme.colors.text, ...theme.typography.label, fontSize: 17 }, description: { color: theme.colors.muted, ...theme.typography.body },
  topicFooter: { alignItems: 'center', borderTopColor: theme.colors.divider, borderTopWidth: 1, flexDirection: 'row', justifyContent: 'space-between', marginTop: theme.spacing.xs, paddingTop: theme.spacing.sm }, action: { color: theme.colors.primary, ...theme.typography.label },
});
