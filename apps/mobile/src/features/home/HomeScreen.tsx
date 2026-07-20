import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Body, Button, Card, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';

export function HomeScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'Home'>) {
  const sessionId = useAppStore((s) => s.currentSessionId);
  return <Screen><Title>Study for citizenship</Title><Body>Practise from the currently published Swedish citizenship content.</Body>
    {sessionId && <Card><Body>You have a practice session in progress.</Body><Button label="Continue studying" onPress={() => navigation.navigate('Question', { sessionId })} /></Card>}
    <Button label="Start practice" onPress={() => navigation.navigate('Topics')} />
    <Button label="Mixed practice" onPress={() => navigation.navigate('PracticeSetup', { mode: 'MIXED' })} />
    <Button label="Mock examination" onPress={() => navigation.navigate('MockExam')} />
    <Button label="Progress" onPress={() => navigation.navigate('Progress')} />
    <Button label="Settings" onPress={() => navigation.navigate('Settings')} />
  </Screen>;
}
