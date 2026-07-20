import { StyleSheet, Text, View } from 'react-native';
import type { MockExamResult } from '../../api/generated/types.gen';
import { Body, Card, Title } from '../../components/ui';
import { theme } from '../../theme/theme';

export function MockResultView({ result }: { result: MockExamResult }) {
  return <View style={styles.list}><Title>{result.passed ? 'Passed' : 'Not passed'}</Title><Card><Text style={styles.score}>{result.correctAnswers}/{result.correctAnswers + result.incorrectAnswers}</Text><Body>{result.percentage}% correct</Body><Body>Time spent: {Math.floor(result.durationSeconds / 60)}m {result.durationSeconds % 60}s</Body><Body>Attempt date: {new Date(result.startedAt).toLocaleDateString()}</Body></Card>
    <Text accessibilityRole="header" style={styles.heading}>Topic breakdown</Text>{result.topics.map((topic) => <Card key={topic.topicId}><Text style={styles.topic}>{topic.topicName}</Text><Text>{topic.correct}/{topic.total} correct · {topic.percentage}%</Text></Card>)}
    <Text accessibilityRole="header" style={styles.heading}>Incorrect and unanswered questions</Text>{result.incorrectQuestions.length === 0 ? <Body>No incorrect answers.</Body> : result.incorrectQuestions.map((question) => <Card key={question.questionId}><Text style={styles.topic}>{question.prompt}</Text><Text>Your answer: {question.selectedAnswerText ?? 'Unanswered'}</Text><Text style={styles.correct}>Correct answer: {question.correctAnswerText}</Text><Text>{question.explanation}</Text></Card>)}
  </View>;
}
const styles = StyleSheet.create({ list: { gap: 16 }, score: { fontSize: 30, fontWeight: '700' }, heading: { fontSize: 20, fontWeight: '700' }, topic: { fontSize: 17, fontWeight: '600' }, correct: { color: theme.colors.success, fontWeight: '600' } });
