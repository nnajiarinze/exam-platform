import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  acceptAiQuestionProposal,
  cancelAiQuestionGenerationJob,
  createAiQuestionGenerationJob,
  getAiQuestionGenerationEligibility,
  getAiQuestionGenerationJob,
  listAiQuestionGenerationJobs,
  listAiQuestionProposals,
  rejectAiQuestionProposal,
  type AiQuestionGenerationEligibilityReason,
  type AiQuestionGenerationJob,
  type AiQuestionAcceptanceResult,
  type AiQuestionProposal,
  type KnowledgeFact,
  type QuestionType,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";
import { questionAcceptanceError } from "../questionAcceptanceErrors";

type Props = { fact: KnowledgeFact };
const terminal = new Set(["COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"]);
const reasonMessages: Record<AiQuestionGenerationEligibilityReason, string> = {
  FACT_NOT_APPROVED: "Approve this Knowledge Fact before generating questions.",
  FACT_RETIRED: "Retired facts cannot be used for question generation.",
  FACT_INACTIVE: "This Knowledge Fact is inactive.",
  FACT_HIERARCHY_INACTIVE: "The content structure containing this Knowledge Fact is inactive.",
  SOURCE_CONTEXT_NOT_READY: "Linked Source content is not ready.",
  INVALID_CONTENT: "This Knowledge Fact contains invalid or unsupported text.",
  UNSUPPORTED_LANGUAGE: "The language is not supported.",
};

export function AiQuestionGenerationWorkspace({ fact }: Props) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [selectedJobId, setSelectedJobId] = useState<string>();
  const [proposalCount, setProposalCount] = useState(3);
  const [questionType, setQuestionType] = useState<QuestionType | "">("SINGLE_CHOICE");
  const [qualityFilter, setQualityFilter] = useState("");
  const [difficultyFilter, setDifficultyFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const eligibility = useQuery({
    queryKey: adminQueryKeys.ai.questionEligibility(fact.id, fact.currentVersionId),
    queryFn: () => unwrap(getAiQuestionGenerationEligibility({ client: contentServiceClient, path: { knowledgeFactId: fact.id } })),
  });
  const canInspect = eligibility.data?.canInspect === true;
  const history = useQuery({
    queryKey: adminQueryKeys.ai.questionHistory(fact.id),
    queryFn: () => unwrap(listAiQuestionGenerationJobs({ client: contentServiceClient, path: { knowledgeFactId: fact.id }, query: { limit: 10 } })),
    enabled: canInspect,
  });
  const effectiveJobId = selectedJobId ?? history.data?.[0]?.id;
  const job = useQuery({
    queryKey: adminQueryKeys.ai.questionJob(fact.id, fact.currentVersionId, effectiveJobId ?? "none"),
    queryFn: () => unwrap(getAiQuestionGenerationJob({ client: contentServiceClient, path: { jobId: effectiveJobId! } })),
    enabled: canInspect && Boolean(effectiveJobId),
    refetchInterval: query => terminal.has(query.state.data?.status ?? "") ? false : 2_000,
  });
  const finished = Boolean(job.data && ["COMPLETED", "PARTIALLY_COMPLETED"].includes(job.data.status));
  const proposals = useQuery({
    queryKey: adminQueryKeys.ai.questionProposals(fact.id, fact.currentVersionId, effectiveJobId ?? "none"),
    queryFn: () => unwrap(listAiQuestionProposals({ client: contentServiceClient, path: { jobId: effectiveJobId! } })),
    enabled: canInspect && finished,
  });
  const create = useMutation({
    mutationFn: () => unwrap(createAiQuestionGenerationJob({ client: contentServiceClient, path: { knowledgeFactId: fact.id }, body: { proposalCount, questionType: questionType || null, idempotencyKey: crypto.randomUUID() } })),
    onSuccess: created => {
      queryClient.setQueryData<AiQuestionGenerationJob[]>(adminQueryKeys.ai.questionHistory(fact.id), current => [created, ...(current ?? []).filter(item => item.id !== created.id)]);
      setSelectedJobId(created.id);
      setOpen(false);
    },
  });
  const cancel = useMutation({
    mutationFn: () => unwrap(cancelAiQuestionGenerationJob({ client: contentServiceClient, path: { jobId: effectiveJobId! } })),
    onSuccess: updated => {
      queryClient.setQueryData(adminQueryKeys.ai.questionJob(fact.id, fact.currentVersionId, updated.id), updated);
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.ai.questionHistory(fact.id) });
    },
  });
  const domainReason = eligibility.data?.reasons[0];
  const permissionReason = eligibility.data && !eligibility.data.canGenerate
    ? eligibility.data.canInspect
      ? "You can review AI question proposals, but only content authors and administrators can generate them."
      : "Your current role does not allow access to AI question generation."
    : undefined;
  const error = eligibility.error || history.error || create.error || job.error || proposals.error || cancel.error;
  const visibleProposals = proposals.data?.filter(proposal => (!qualityFilter || proposal.intelligenceAssessment.qualityLevel === qualityFilter)
    && (!difficultyFilter || proposal.intelligenceAssessment.difficulty === difficultyFilter)
    && (!typeFilter || proposal.questionType === typeFilter));

  return <section className="card ai-editorial" aria-labelledby="ai-question-heading">
    <div className="actions">
      <div><h2 id="ai-question-heading">AI Question Proposals</h2><p>Generate AI-assisted question proposals from this approved Knowledge Fact. Proposals must be reviewed before they can become learner-facing Questions.</p></div>
      {eligibility.data?.canInspect !== false && <button type="button" disabled={!eligibility.data?.eligible || !eligibility.data?.canGenerate} onClick={() => setOpen(true)}>Generate Questions</button>}
    </div>
    {eligibility.isPending && <p role="status">Checking question-generation eligibility…</p>}
    {domainReason && <p role="status" className="warning">{reasonMessages[domainReason]}</p>}
    {permissionReason && <p role="status" className="warning">{permissionReason}</p>}
    {open && <section className="card split-confirmation" role="dialog" aria-modal="true" aria-labelledby="question-generation-dialog-title">
      <h3 id="question-generation-dialog-title">Generate Question Proposals</h3>
      <p><strong>Knowledge Fact:</strong> {fact.canonicalStatement}</p><p><strong>Approval:</strong> {fact.reviewStatus}</p><p><strong>Learning Objective:</strong> {fact.learningObjectiveTitle}</p><p><strong>Language:</strong> Swedish</p><p><strong>Linked Sources:</strong> {fact.sourceCount}</p>
      <label>Number of proposals<select aria-label="Number of question proposals" value={proposalCount} onChange={event => setProposalCount(Number(event.target.value))}><option value={1}>1</option><option value={2}>2</option><option value={3}>3</option></select></label>
      <label>Question type<select aria-label="Question type" value={questionType} onChange={event => setQuestionType(event.target.value as QuestionType | "")}><option value="">Any supported type</option><option value="SINGLE_CHOICE">Single choice</option><option value="TRUE_FALSE">True / false</option><option value="MULTIPLE_CHOICE">Multiple choice</option></select></label>
      <p className="warning">AI-generated questions are proposals only. They must be reviewed before they can become learner-facing content.</p>
      <div className="actions"><button type="button" className="secondary" onClick={() => setOpen(false)}>Cancel</button><button type="button" disabled={create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Starting…" : "Generate"}</button></div>
    </section>}
    {canInspect && history.isPending && <p role="status">Loading previous question-generation jobs…</p>}
    {canInspect && history.data?.length === 0 && <p className="muted">No question-generation job has been started for this Knowledge Fact yet.</p>}
    {canInspect && history.data && history.data.length > 1 && <label>Question-generation history<select aria-label="Question-generation history" value={effectiveJobId ?? ""} onChange={event => setSelectedJobId(event.target.value)}>{history.data.map(item => <option key={item.id} value={item.id}>{new Date(item.createdAt).toLocaleString()} · {item.status.replaceAll("_", " ")} · {item.requestedQuestionType?.replaceAll("_", " ") ?? "Any type"} · {item.proposalCount} proposals</option>)}</select></label>}
    {job.data && <section className="ai-job-summary" aria-live="polite" aria-label="Question-generation job">
      <div className="actions"><span className="status-badge">{job.data.status.replaceAll("_", " ")}</span><span>{job.data.proposalCount} of {job.data.requestedCount} proposals generated</span></div>
      <dl className="metadata-grid"><div><dt>Job ID</dt><dd><code>{job.data.id}</code></dd></div><div><dt>Requested by</dt><dd>{job.data.requestedBy}</dd></div><div><dt>Created</dt><dd>{new Date(job.data.createdAt).toLocaleString()}</dd></div>{job.data.startedAt && <div><dt>Started</dt><dd>{new Date(job.data.startedAt).toLocaleString()}</dd></div>}{job.data.completedAt && <div><dt>Completed</dt><dd>{new Date(job.data.completedAt).toLocaleString()}</dd></div>}</dl>
      {["QUEUED", "RUNNING"].includes(job.data.status) && <button type="button" className="secondary" disabled={cancel.isPending} onClick={() => cancel.mutate()}>{cancel.isPending ? "Cancelling…" : "Cancel Generation"}</button>}
      {job.data.status === "FAILED" && <><p role="alert" className="error">{job.data.errorMessage ?? "Question generation could not be completed."}</p>{eligibility.data?.eligible && eligibility.data.canGenerate && <button type="button" className="secondary" onClick={() => setOpen(true)}>Generate Again</button>}</>}
      {job.data.status === "CANCELLED" && <><p role="status">This generation job was cancelled.</p>{eligibility.data?.eligible && eligibility.data.canGenerate && <button type="button" className="secondary" onClick={() => setOpen(true)}>Generate Again</button>}</>}
      {job.data.resultType && job.data.resultType !== "QUESTIONS_PROPOSED" && <p role="status">No question was invented: {job.data.resultType.replaceAll("_", " ").toLowerCase()}.</p>}
    </section>}
    {error && <p role="alert" className="error">{error.message}</p>}
    {proposals.data && proposals.data.length > 0 && <div className="actions" aria-label="Question proposal filters"><label>Quality<select aria-label="Filter by quality" value={qualityFilter} onChange={event => setQualityFilter(event.target.value)}><option value="">All</option>{["EXCELLENT","GOOD","ACCEPTABLE","NEEDS_REVIEW","REJECTED"].map(value => <option key={value} value={value}>{value.replaceAll("_"," ")}</option>)}</select></label><label>Difficulty<select aria-label="Filter by difficulty" value={difficultyFilter} onChange={event => setDifficultyFilter(event.target.value)}><option value="">All</option>{["VERY_EASY","EASY","MEDIUM","HARD","VERY_HARD"].map(value => <option key={value} value={value}>{value.replaceAll("_"," ")}</option>)}</select></label><label>Type<select aria-label="Filter by question type" value={typeFilter} onChange={event => setTypeFilter(event.target.value)}><option value="">All</option>{["SINGLE_CHOICE","TRUE_FALSE","MULTIPLE_CHOICE"].map(value => <option key={value} value={value}>{value.replaceAll("_"," ")}</option>)}</select></label></div>}
    {visibleProposals?.length === 0 && proposals.data && proposals.data.length > 0 && <p className="muted">No proposals match these filters.</p>}
    {finished && proposals.data?.length === 0 && <p className="muted" role="status">This job did not produce any question proposals.</p>}
    <div className="proposal-grid">{visibleProposals?.map(proposal => <QuestionProposalCard key={proposal.id} proposal={proposal} factId={fact.id} factVersionId={fact.currentVersionId} jobId={effectiveJobId!} canAccept={eligibility.data?.canGenerate === true} />)}</div>
  </section>;
}

function QuestionProposalCard({ proposal, factId, factVersionId, jobId, canAccept }: { proposal: AiQuestionProposal; factId: string; factVersionId: string; jobId: string; canAccept: boolean }) {
  const queryClient = useQueryClient();const [rejectConfirm, setRejectConfirm] = useState(false);const [acceptConfirm, setAcceptConfirm] = useState(false);const [reason, setReason] = useState("");const [accepted, setAccepted] = useState<AiQuestionAcceptanceResult>();
  const reject = useMutation({ mutationFn: () => unwrap(rejectAiQuestionProposal({ client: contentServiceClient, path: { proposalId: proposal.id }, body: { reason: reason || null, version: proposal.version } })), onSuccess: () => { setRejectConfirm(false); queryClient.invalidateQueries({ queryKey: adminQueryKeys.ai.questionProposals(factId, factVersionId, jobId) }); } });
  const accept = useMutation({
    mutationFn: () => unwrap(acceptAiQuestionProposal({ client: contentServiceClient, path: { proposalId: proposal.id }, body: { version: proposal.version } })),
    onSuccess: result => {
      setAccepted(result);setAcceptConfirm(false);
      queryClient.setQueryData<AiQuestionProposal[]>(adminQueryKeys.ai.questionProposals(factId, factVersionId, jobId), current => current?.map(item => item.id === proposal.id ? { ...item, status: "ACCEPTED", acceptedQuestionId: result.questionId } : item));
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.ai.questionProposals(factId, factVersionId, jobId) });
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.questions.all });
    },
  });
  const intelligence=proposal.intelligenceAssessment;
  const acceptedQuestionId=accepted?.questionId ?? proposal.acceptedQuestionId;
  const isAccepted=proposal.status === "ACCEPTED" || Boolean(accepted);
  const blockingFindings=intelligence.findings?.some(finding => finding.blocking) === true;
  const acceptanceEligible=!isAccepted && proposal.status === "PROPOSED" && proposal.validationStatus === "VALID" && intelligence.evaluationStatus === "EVALUATED" && intelligence.passedValidation === true && !blockingFindings && canAccept;
  return <article className="card ai-editorial-proposal">
    <div className="actions"><span className="status-badge">AI proposal</span><span className={`status-badge status-${isAccepted ? "accepted" : proposal.status.toLowerCase()}`}>{isAccepted ? "Accepted" : proposal.status === "REJECTED" ? "Rejected" : "Proposed"}</span><span className="status-badge">{proposal.questionType.replaceAll("_", " ")}</span>{intelligence.evaluationStatus === "EVALUATED" && <><span className="status-badge">{intelligence.qualityLevel?.replaceAll("_"," ")} · {intelligence.overallQualityScore}/100</span><span className="status-badge">{intelligence.difficulty?.replaceAll("_"," ")}</span><span className="status-badge">{intelligence.bloomsLevel}</span></>}</div>
    {intelligence.evaluationStatus === "NOT_EVALUATED" && <p className="muted">Quality not evaluated (created before the intelligence engine).</p>}
    <h3>{proposal.questionText}</h3>
    <ol className="question-proposal-options">{proposal.answerOptions.map(option => <li key={option.id} className={option.correct ? "question-proposal-correct" : undefined}><strong>{option.optionKey}.</strong> {option.text} {option.correct && <span className="status-badge">Proposed correct answer</span>}</li>)}</ol>
    <p><strong>Language:</strong> {proposal.language}</p><p><strong>Explanation:</strong> {proposal.explanation}</p><p><strong>Editorial rationale:</strong> {proposal.rationale}</p>
    {intelligence.evaluationStatus === "EVALUATED" && <details><summary>Quality findings ({intelligence.findings?.length ?? 0})</summary><p><strong>Complexity:</strong> {intelligence.complexity} · <strong>Reading:</strong> {intelligence.estimatedReadingSeconds}s · <strong>Intent:</strong> {intelligence.generationIntent}</p>{intelligence.qualityRationale && <p>{intelligence.qualityRationale}</p>}{intelligence.findings?.map(finding => <p key={`${finding.code}-${finding.field}`} className={finding.blocking ? "error" : finding.severity === "WARNING" ? "warning" : "muted"}><strong>{finding.code.replaceAll("_"," ")}:</strong> {finding.message}</p>)}</details>}
    <h4>Grounding evidence</h4><p><strong>Knowledge Fact version:</strong> <code>{proposal.targetKnowledgeFactVersionId}</code></p>{proposal.evidence.map(evidence => <blockquote key={evidence.id}>“{evidence.quote ?? evidence.supportedClaim}”<footer>{evidence.sourceTitle ?? "Approved Knowledge Fact"}{evidence.sourceChecksum && <> · checksum <code>{evidence.sourceChecksum.slice(0,12)}…</code></>}</footer></blockquote>)}
    {proposal.warnings.length > 0 && <p className="warning"><strong>Warnings:</strong> {proposal.warnings.join("; ")}</p>}
    <section aria-label="AI provenance"><h4>AI provenance</h4><p><small>{proposal.provider} · {proposal.model} · {proposal.promptVersion} · generated {new Date(proposal.createdAt).toLocaleString()}{proposal.totalTokens == null ? "" : ` · ${proposal.totalTokens} tokens`}</small></p></section>
    {isAccepted && <div className="success" role="status"><strong>Accepted</strong><p>A canonical Question was created as DRAFT / UNREVIEWED.</p>{acceptedQuestionId && <><p>Question ID: <code>{acceptedQuestionId}</code></p><Link className="button-link" to={`/questions/${acceptedQuestionId}`}>View Question</Link></>}{proposal.acceptedBy && <p>Accepted by {proposal.acceptedBy}{proposal.acceptedAt ? ` on ${new Date(proposal.acceptedAt).toLocaleString()}` : ""}</p>}</div>}
    {acceptanceEligible && !acceptConfirm && !rejectConfirm && <div className="actions"><button type="button" onClick={() => setAcceptConfirm(true)}>Accept Proposal</button><button type="button" className="secondary" onClick={() => setRejectConfirm(true)}>Reject Proposal</button></div>}
    {proposal.status === "PROPOSED" && !acceptanceEligible && !isAccepted && !rejectConfirm && <p className="muted">{canAccept ? "This proposal is not eligible for acceptance." : "Only content authors and administrators can accept proposals."}</p>}
    {acceptConfirm && <section className="card split-confirmation" role="dialog" aria-modal="true" aria-labelledby={`accept-${proposal.id}`}><h4 id={`accept-${proposal.id}`}>Accept this proposal?</h4><p>This creates one canonical Question as <strong>DRAFT / UNREVIEWED</strong>. The proposal becomes immutable and cannot be accepted again.</p><div className="actions"><button type="button" className="secondary" disabled={accept.isPending} onClick={() => setAcceptConfirm(false)}>Cancel</button><button type="button" disabled={accept.isPending} onClick={() => accept.mutate()}>{accept.isPending ? "Accepting…" : "Confirm Acceptance"}</button></div></section>}
    {rejectConfirm && <section className="card" role="dialog" aria-modal="true" aria-label="Confirm proposal rejection"><label>Optional rejection reason<textarea maxLength={500} rows={3} value={reason} onChange={event => setReason(event.target.value)} /></label><div className="actions"><button type="button" className="secondary" onClick={() => setRejectConfirm(false)}>Keep Proposal</button><button type="button" disabled={reject.isPending} onClick={() => reject.mutate()}>{reject.isPending ? "Rejecting…" : "Confirm Rejection"}</button></div></section>}
    {proposal.status === "REJECTED" && proposal.rejectionReason && <p><strong>Rejection reason:</strong> {proposal.rejectionReason}</p>}
    {reject.error && <p role="alert" className="error">{reject.error.message}</p>}{accept.error && <p role="alert" className="error">{questionAcceptanceError(accept.error)}</p>}
  </article>;
}
