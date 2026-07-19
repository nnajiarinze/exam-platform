import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { learningApi } from '../../api/learningApi';
import { friendlyError } from '../../api/errors';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { ErrorState, Loading, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { TopicList } from './TopicList';

export function TopicsScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'Topics'>) {
  const identity = useAppStore((s) => s.learnerIdentity);
  const query = useQuery({ queryKey: ['subjects'], queryFn: () => learningApi.subjects(identity), enabled: Boolean(identity) });
  return <Screen><Title>Choose a topic</Title>{!identity ? <ErrorState message="No learner identity is configured." /> : query.isPending ? <Loading label="Loading topics…" /> : query.isError ? <ErrorState message={friendlyError(query.error)} retry={() => query.refetch()} /> : <TopicList subjects={query.data} onSelect={(topicId, topicName) => navigation.navigate('PracticeSetup', { mode: 'TOPIC', topicId, topicName })} />}</Screen>;
}
