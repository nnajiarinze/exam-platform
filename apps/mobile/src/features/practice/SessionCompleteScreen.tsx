import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { StyleSheet, Text } from 'react-native';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Button, Card, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { sessionAccuracy } from './summary';

export function SessionCompleteScreen({ navigation, route }: NativeStackScreenProps<RootStackParamList, 'SessionComplete'>) {
  const correct = useAppStore((s) => s.correctAnswers); const setSession = useAppStore((s) => s.setSession);
  const finish = () => { setSession(undefined); navigation.popTo('Home'); };
  return <Screen><Title>Practice complete</Title><Card><Text style={styles.metric}>Total answered: {route.params.total}</Text><Text style={styles.metric}>Correct answers: {correct}</Text><Text style={styles.metric}>Accuracy: {sessionAccuracy(correct, route.params.total)}%</Text></Card><Button label="Finish" onPress={finish} /></Screen>;
}
const styles = StyleSheet.create({ metric: { fontSize: 18 } });
