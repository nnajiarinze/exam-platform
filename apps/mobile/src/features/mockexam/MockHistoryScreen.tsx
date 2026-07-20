import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { friendlyError } from '../../api/errors';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { ErrorState, Loading, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { MockHistoryList } from './MockHistoryList';

export function MockHistoryScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'MockHistory'>) {
  const identity = useAppStore((s) => s.learnerIdentity);
  const query = useQuery({ queryKey: ['mock-history'], queryFn: () => learningApi.mockHistory(identity) });
  return <Screen><Title>Mock history</Title>{query.isPending ? <Loading /> : query.isError ? <ErrorState message={friendlyError(query.error)} retry={() => query.refetch()} /> : <MockHistoryList attempts={query.data} onSelect={(attemptId) => navigation.navigate('MockResults', { attemptId })} />}</Screen>;
}
