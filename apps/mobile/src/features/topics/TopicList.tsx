import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Subject } from '../../api/generated/types.gen';
import { Card } from '../../components/ui';
import { theme } from '../../theme/theme';

export function TopicList({ subjects, onSelect }: { subjects: Subject[]; onSelect: (topicId: string, topicName: string) => void }) {
  return <View style={styles.list}>{subjects.map((subject) => <Card key={subject.id}><Text accessibilityRole="header" style={styles.subject}>{subject.name}</Text>{subject.topics.map((topic) => <Pressable accessibilityRole="button" key={topic.id} onPress={() => onSelect(topic.id, topic.name)} style={styles.topic}><Text style={styles.topicName}>{topic.name}</Text>{topic.description && <Text style={styles.description}>{topic.description}</Text>}</Pressable>)}</Card>)}</View>;
}
const styles = StyleSheet.create({ list: { gap: 16 }, subject: { fontSize: 20, fontWeight: '700' }, topic: { borderTopColor: theme.colors.border, borderTopWidth: 1, gap: 4, paddingVertical: 12 }, topicName: { color: theme.colors.primary, fontSize: 17, fontWeight: '600' }, description: { color: theme.colors.muted } });
