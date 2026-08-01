import { useQuery } from '@tanstack/react-query';
import { environment } from '../../app/config/environment';
import { AsyncState } from '../../components/AsyncState';

type Definition = {
  examId: string;
  name: string;
  description: string;
  questionCount: number;
  durationMinutes: number | null;
  timed: boolean;
  passPercentage: number;
};

function learningBaseUrl(): string {
  const gateway = environment.oidcAuthority.split('/auth/realms/')[0];
  return `${gateway}/learning`;
}

async function loadDefinition(): Promise<Definition> {
  const response = await fetch(`${learningBaseUrl()}/api/v1/mock-exams/configuration?examId=sverige-i-fokus-v1`);
  if (!response.ok) throw new Error('Unable to load the active mock examination definition.');
  return response.json() as Promise<Definition>;
}

export function MockExamDefinitionPage() {
  const query = useQuery({ queryKey: ['mock-exam-definition', environment.appEnvironment], queryFn: loadDefinition });
  return <>
    <header className="page-header"><div><span className="eyebrow">Learner assessment</span><h1>Exam Definition</h1><p>The active runtime definition used for new immutable learner attempts.</p></div></header>
    <AsyncState loading={query.isPending} error={query.error}>
      {query.data ? <section className="card" aria-labelledby="active-definition"><h2 id="active-definition">Current active definition</h2>
        <div className="report-grid">
          <Metric label="Definition">{query.data.name}</Metric>
          <Metric label="Question count">{query.data.questionCount}</Metric>
          <Metric label="Timing">{query.data.durationMinutes == null ? 'Untimed' : `${query.data.durationMinutes} minutes`}</Metric>
          <Metric label="Pass threshold">{query.data.passPercentage}%</Metric>
          <Metric label="Release">ACTIVE release at attempt start</Metric>
          <Metric label="Coverage">Balanced curriculum areas, Topics, and Objectives</Metric>
        </div>
        <p>{query.data.description}</p>
        <p className="notice">Every attempt receives a deterministic, duplicate-free question set and an immutable question, option, answer, explanation, Topic, Objective, and lesson-link snapshot.</p>
      </section> : null}
    </AsyncState>
  </>;
}

function Metric({ label, children }: { label: string; children: React.ReactNode }) {
  return <article className="metric"><span>{label}</span><strong>{children}</strong></article>;
}
