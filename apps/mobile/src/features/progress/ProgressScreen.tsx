import { useQuery } from '@tanstack/react-query';
import { learningApi } from '../../api/learningApi';
import { friendlyError } from '../../api/errors';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { ErrorState, Loading, Title } from '../../components/ui';
import { ProgressList } from './ProgressList';

export function ProgressScreen() {
  const identity = useAppStore((s) => s.learnerIdentity);
  const progress = useQuery({ queryKey: ['progress'], queryFn: () => learningApi.progress(identity) });
  const subjects = useQuery({ queryKey: ['subjects'], queryFn: () => learningApi.subjects(identity) });
  if (progress.isPending) return <Screen scroll={false}><Loading label="Loading progress…" /></Screen>;
  if (progress.isError) return <Screen><Title>Progress</Title><ErrorState message={friendlyError(progress.error)} retry={() => progress.refetch()} /></Screen>;
  const topicNames = Object.fromEntries((subjects.data ?? []).flatMap((subject) => subject.topics.map((topic) => [topic.id, topic.name])));
  return <Screen><Title>Progress</Title><ProgressList progress={progress.data} topicNames={topicNames} /></Screen>;
}
