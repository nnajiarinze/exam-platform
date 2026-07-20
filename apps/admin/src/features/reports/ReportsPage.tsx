import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { getContentHealthReport, getLearnerHealthReport, getReleaseHealthReport, getReviewHealthReport, getSourceHealthReport } from '../../api/generated';
import { contentServiceClient } from '../../api/client/contentServiceClient';
import { unwrap } from '../../api/client/adminApi';
import { AsyncState } from '../../components/AsyncState';

type Report = Record<string, unknown>;
const value = (report: Report | undefined, key: string) => report?.[key] == null ? '—' : String(report[key]);
const nested = (report: Report | undefined, group: string, key: string) => { const section=report?.[group];return typeof section==='object'&&section!==null&&key in section?String((section as Report)[key]):'—'; };
function Metric({label,children}:{label:string;children:ReactNode}){return <div className="card metric"><span>{label}</span><strong>{children}</strong></div>}
function ReportState({query,children}:{query:{isPending:boolean;error:unknown};children:ReactNode}){return <AsyncState loading={query.isPending} error={query.error}>{children}</AsyncState>}

export function ReportsPage(){
  const content=useQuery({queryKey:['reports','content'],queryFn:()=>unwrap(getContentHealthReport({client:contentServiceClient}))});
  const review=useQuery({queryKey:['reports','review'],queryFn:()=>unwrap(getReviewHealthReport({client:contentServiceClient}))});
  const source=useQuery({queryKey:['reports','source'],queryFn:()=>unwrap(getSourceHealthReport({client:contentServiceClient}))});
  const release=useQuery({queryKey:['reports','release'],queryFn:()=>unwrap(getReleaseHealthReport({client:contentServiceClient}))});
  const learner=useQuery({queryKey:['reports','learner'],queryFn:()=>unwrap(getLearnerHealthReport({client:contentServiceClient}))});
  const c=content.data as Report|undefined,r=review.data as Report|undefined,s=source.data as Report|undefined,l=learner.data as Report|undefined;
  return <><header className="page-header"><div><span className="eyebrow">Operations</span><h1>Platform health</h1><p>Aggregate operational signals. Learner statistics contain no learner identity or question content.</p></div></header>
    <section aria-labelledby="content-health"><h2 id="content-health">Content health</h2><ReportState query={content}><div className="report-grid"><Metric label="Questions">{nested(c,'questions','total')}</Metric><Metric label="Approved questions">{nested(c,'questions','approved')}</Metric><Metric label="Knowledge facts">{nested(c,'knowledgeFacts','total')}</Metric><Metric label="Approved facts">{nested(c,'knowledgeFacts','approved')}</Metric><Metric label="Sources">{nested(c,'sources','total')}</Metric><Metric label="Questions missing facts">{value(c,'questionsMissingFacts')}</Metric><Metric label="Facts without questions">{value(c,'factsWithoutQuestions')}</Metric><Metric label="Objectives without questions">{value(c,'objectivesWithoutQuestions')}</Metric><Metric label="Topics without questions">{value(c,'topicsWithoutQuestions')}</Metric><Metric label="Subjects without questions">{value(c,'subjectsWithoutQuestions')}</Metric></div></ReportState></section>
    <section aria-labelledby="review-health"><h2 id="review-health">Review health</h2><ReportState query={review}><div className="report-grid"><Metric label="Pending">{value(r,'pending')}</Metric><Metric label="Assigned to me">{value(r,'assignedToMe')}</Metric><Metric label="Average age (hours)">{value(r,'averageAgeHours')}</Metric><Metric label="Rejected today">{value(r,'rejectedToday')}</Metric><Metric label="Requires update today">{value(r,'requiresUpdateToday')}</Metric></div></ReportState></section>
    <section aria-labelledby="source-health"><h2 id="source-health">Source health</h2><ReportState query={source}><div className="report-grid"><Metric label="Retired">{value(s,'retired')}</Metric><Metric label="Requires update">{value(s,'requiresUpdate')}</Metric><Metric label="Unused">{value(s,'unused')}</Metric><Metric label="Facts using retired sources">{value(s,'factsReferencingRetired')}</Metric><Metric label="Facts using outdated sources">{value(s,'factsReferencingOutdated')}</Metric></div></ReportState></section>
    <section aria-labelledby="release-health"><h2 id="release-health">Release health</h2><ReportState query={release}><pre className="json-preview">{JSON.stringify(release.data,null,2)}</pre></ReportState></section>
    <section aria-labelledby="learner-health"><h2 id="learner-health">Learner runtime health</h2><ReportState query={learner}><div className="report-grid"><Metric label="Practice sessions">{value(l,'practiceSessions')}</Metric><Metric label="Mock exams">{value(l,'mockExams')}</Metric><Metric label="Active mock exams">{value(l,'activeMockExams')}</Metric><Metric label="Completed mock exams">{value(l,'completedMockExams')}</Metric><Metric label="Expired mock exams">{value(l,'expiredMockExams')}</Metric><Metric label="Average score (%)">{value(l,'averageScore')}</Metric><Metric label="Pass rate (%)">{value(l,'passRate')}</Metric><Metric label="Average duration (seconds)">{value(l,'averageDurationSeconds')}</Metric></div></ReportState></section>
  </>;
}
