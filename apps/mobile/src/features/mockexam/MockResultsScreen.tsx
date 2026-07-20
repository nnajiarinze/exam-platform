import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { friendlyError } from '../../api/errors';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Button, ErrorState, Loading } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { MockResultView } from './MockResultView';

export function MockResultsScreen({ navigation, route }: NativeStackScreenProps<RootStackParamList, 'MockResults'>) {
  const identity = useAppStore((s) => s.learnerIdentity);
  const query = useQuery({ queryKey: ['mock-results', route.params.attemptId], queryFn: () => learningApi.mockResults(identity, route.params.attemptId) });
  if (query.isPending) return <Screen scroll={false}><Loading label="Loading results…" /></Screen>;
  if (query.isError) return <Screen><ErrorState message={friendlyError(query.error)} retry={() => query.refetch()} /></Screen>;
  return <Screen><MockResultView result={query.data} /><Button label="Review answers" onPress={() => navigation.navigate('MockAnswerReview', { attemptId: route.params.attemptId })} /><Button label="Retake mock exam" onPress={() => navigation.navigate('MockExam')} /><Button label="Return home" onPress={() => navigation.popTo('Home')} /><Button label="View history" onPress={() => navigation.navigate('MockHistory')} /></Screen>;
}
