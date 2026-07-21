import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  cancelAiQuestionGenerationJob,
  createAiQuestionGenerationJob,
  getAiQuestionGenerationJob,
  listAiQuestionProposals,
  rejectAiQuestionProposal,
  type AiQuestionProposal,
  type KnowledgeFact,
  type QuestionType,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";

type Props = { fact: KnowledgeFact; canGenerate: boolean; canInspect: boolean; sourcesReady: boolean };
const terminal = new Set(["COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"]);

export function AiQuestionGenerationWorkspace({ fact, canGenerate, canInspect, sourcesReady }: Props) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [jobId, setJobId] = useState<string>();
  const [proposalCount, setProposalCount] = useState(3);
  const [questionType, setQuestionType] = useState<QuestionType | "">("SINGLE_CHOICE");
  const eligible = fact.reviewStatus === "APPROVED" && fact.status === "ACTIVE" && sourcesReady;
  const reason = fact.reviewStatus !== "APPROVED" ? "Fact must be approved before generating questions."
    : fact.status === "RETIRED" ? "Retired facts cannot be used."
    : fact.status !== "ACTIVE" ? "Fact must be active before generating questions."
    : !sourcesReady ? "Every linked Source needs stored content before generation."
    : !canGenerate ? "You do not have permission to generate questions." : undefined;
  const job = useQuery({
    queryKey: adminQueryKeys.ai.questionJob(fact.id, fact.currentVersionId, jobId ?? "pending"),
    queryFn: () => unwrap(getAiQuestionGenerationJob({ client: contentServiceClient, path: { jobId: jobId! } })),
    enabled: Boolean(jobId),
    refetchInterval: query => terminal.has(query.state.data?.status ?? "") ? false : 750,
  });
  const finished = Boolean(job.data && ["COMPLETED", "PARTIALLY_COMPLETED"].includes(job.data.status));
  const proposals = useQuery({
    queryKey: adminQueryKeys.ai.questionProposals(fact.id, fact.currentVersionId, jobId ?? "pending"),
    queryFn: () => unwrap(listAiQuestionProposals({ client: contentServiceClient, path: { jobId: jobId! } })),
    enabled: finished,
  });
  const create = useMutation({
    mutationFn: () => unwrap(createAiQuestionGenerationJob({ client: contentServiceClient, path: { knowledgeFactId: fact.id }, body: { proposalCount, questionType: questionType || null, idempotencyKey: crypto.randomUUID() } })),
    onSuccess: created => { setJobId(created.id); setOpen(false); },
  });
  const cancel = useMutation({
    mutationFn: () => unwrap(cancelAiQuestionGenerationJob({ client: contentServiceClient, path: { jobId: jobId! } })),
    onSuccess: updated => queryClient.setQueryData(adminQueryKeys.ai.questionJob(fact.id, fact.currentVersionId, jobId!), updated),
  });
  if (!canInspect) return null;
  const error = create.error || job.error || proposals.error || cancel.error;
  return <section className="card ai-editorial" aria-labelledby="ai-question-heading">
    <div className="actions"><div><h2 id="ai-question-heading">AI question proposals</h2><p>Generate grounded assessment drafts from this approved Knowledge Fact.</p></div><button type="button" disabled={!eligible || !canGenerate} onClick={() => setOpen(true)}>Generate questions</button></div>
    <p className="muted">AI-generated questions are proposals only. They must be reviewed before they can become learner-facing content.</p>
    {reason && <p role="status" className="warning">{reason}</p>}
    {open && <section className="card split-confirmation" role="dialog" aria-modal="true" aria-labelledby="question-generation-dialog-title">
      <h3 id="question-generation-dialog-title">Generate question proposals</h3>
      <p><strong>Knowledge Fact:</strong> {fact.canonicalStatement}</p><p><strong>Approval:</strong> {fact.reviewStatus}</p><p><strong>Learning Objective:</strong> {fact.learningObjectiveTitle}</p><p><strong>Language:</strong> Swedish</p><p><strong>Linked Sources:</strong> {fact.sourceCount}</p>
      <label>Number of proposals<select aria-label="Number of question proposals" value={proposalCount} onChange={event => setProposalCount(Number(event.target.value))}><option value={1}>1</option><option value={2}>2</option><option value={3}>3</option></select></label>
      <label>Question type<select aria-label="Question type" value={questionType} onChange={event => setQuestionType(event.target.value as QuestionType | "")}><option value="">Any supported type</option><option value="SINGLE_CHOICE">Single choice</option><option value="TRUE_FALSE">True / false</option><option value="MULTIPLE_CHOICE">Multiple choice</option></select></label>
      <p className="warning">AI-generated questions are proposals only. They must be reviewed before they can become learner-facing content.</p>
      <div className="actions"><button type="button" className="secondary" onClick={() => setOpen(false)}>Cancel</button><button type="button" disabled={create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Starting…" : "Generate"}</button></div>
    </section>}
    {job.data && <div aria-live="polite"><p><strong>Status:</strong> {job.data.status.replaceAll("_", " ")}</p>{["QUEUED", "RUNNING"].includes(job.data.status) && <button type="button" className="secondary" disabled={cancel.isPending} onClick={() => cancel.mutate()}>Cancel generation</button>}{job.data.status === "FAILED" && <p role="alert" className="error">{job.data.errorMessage ?? "Question generation could not be completed."}</p>}{job.data.resultType && job.data.resultType !== "QUESTIONS_PROPOSED" && <p role="status">No question was invented: {job.data.resultType.replaceAll("_", " ").toLowerCase()}.</p>}{terminal.has(job.data.status) && <button type="button" className="secondary" onClick={() => setJobId(undefined)}>Start another generation</button>}</div>}
    {error && <p role="alert" className="error">{error.message}</p>}
    <div className="proposal-grid">{proposals.data?.map(proposal => <QuestionProposalCard key={proposal.id} proposal={proposal} factId={fact.id} factVersionId={fact.currentVersionId} jobId={jobId!} />)}</div>
  </section>;
}

function QuestionProposalCard({ proposal, factId, factVersionId, jobId }: { proposal: AiQuestionProposal; factId: string; factVersionId: string; jobId: string }) {
  const queryClient = useQueryClient();const [confirm, setConfirm] = useState(false);const [reason, setReason] = useState("");
  const reject = useMutation({ mutationFn: () => unwrap(rejectAiQuestionProposal({ client: contentServiceClient, path: { proposalId: proposal.id }, body: { reason: reason || null, version: proposal.version } })), onSuccess: () => { setConfirm(false); queryClient.invalidateQueries({ queryKey: adminQueryKeys.ai.questionProposals(factId, factVersionId, jobId) }); } });
  return <article className="card ai-editorial-proposal"><div className="actions"><span className="status-badge">AI proposal</span><span className="status-badge">{proposal.status}</span><span className="status-badge">{proposal.questionType.replaceAll("_", " ")}</span></div><h3>{proposal.questionText}</h3><ol className="question-proposal-options">{proposal.answerOptions.map(option => <li key={option.id} className={option.correct ? "question-proposal-correct" : undefined}><strong>{option.optionKey}.</strong> {option.text} {option.correct && <span className="status-badge">Proposed correct answer</span>}</li>)}</ol><p><strong>Explanation:</strong> {proposal.explanation}</p><p><strong>Editorial rationale:</strong> {proposal.rationale}</p><h4>Grounding evidence</h4>{proposal.evidence.map(evidence => <blockquote key={evidence.id}>“{evidence.quote ?? evidence.supportedClaim}”<footer>{evidence.sourceTitle ?? "Approved Knowledge Fact"}</footer></blockquote>)}{proposal.warnings.length > 0 && <p className="warning"><strong>Warnings:</strong> {proposal.warnings.join("; ")}</p>}<p><small>{proposal.provider} · {proposal.model} · {proposal.promptVersion}{proposal.totalTokens == null ? "" : ` · ${proposal.totalTokens} tokens`}</small></p>{proposal.status === "PROPOSED" && !confirm && <button type="button" className="secondary" onClick={() => setConfirm(true)}>Reject proposal</button>}{confirm && <section className="card" role="dialog" aria-modal="true" aria-label="Confirm proposal rejection"><label>Optional rejection reason<textarea maxLength={500} rows={3} value={reason} onChange={event => setReason(event.target.value)} /></label><div className="actions"><button type="button" className="secondary" onClick={() => setConfirm(false)}>Keep proposal</button><button type="button" disabled={reject.isPending} onClick={() => reject.mutate()}>{reject.isPending ? "Rejecting…" : "Confirm rejection"}</button></div></section>}{proposal.status === "REJECTED" && proposal.rejectionReason && <p><strong>Rejection reason:</strong> {proposal.rejectionReason}</p>}{reject.error && <p role="alert" className="error">{reject.error.message}</p>}</article>;
}
