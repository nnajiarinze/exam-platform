import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  acceptAiEditorialProposal,
  acceptAiEditorialSplit,
  cancelAiEditorialJob,
  createAiEditorialJob,
  dismissAiEditorialFinding,
  editAiEditorialProposal,
  getAiEditorialJob,
  listAiEditorialFindings,
  listAiEditorialProposals,
  rejectAiEditorialProposal,
  type AiEditorialFinding,
  type AiEditorialOperation,
  type AiEditorialProposal,
  type KnowledgeFact,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";

type Props = { fact: KnowledgeFact; enabled: boolean; canMutate: boolean; sourcesReady: boolean };
const terminal = new Set(["COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED"]);
const analysisOperations: { value: AiEditorialOperation; label: string }[] = [
  { value: "CHECK_SOURCE_SUPPORT", label: "Check source support" },
  { value: "DETECT_AMBIGUITY", label: "Detect ambiguity" },
  { value: "EDITORIAL_REVIEW_NOTES", label: "Editorial review notes" },
];
const mutationOperations: { value: AiEditorialOperation; label: string }[] = [
  { value: "REWRITE_FOR_CLARITY", label: "Rewrite for clarity" },
  { value: "SIMPLIFY_LANGUAGE", label: "Simplify language" },
  { value: "MAKE_ATOMIC", label: "Check atomicity" },
  { value: "SPLIT_FACT", label: "Split fact" },
];

export function AiEditorialWorkspace({ fact, enabled, canMutate, sourcesReady }: Props) {
  const queryClient = useQueryClient();
  const editable = fact.status === "DRAFT" && ["UNREVIEWED", "REQUIRES_UPDATE"].includes(fact.reviewStatus);
  const options = canMutate && editable ? [...mutationOperations, ...analysisOperations] : analysisOperations;
  const [jobId, setJobId] = useState<string>();
  const [operation, setOperation] = useState<AiEditorialOperation>(options[0]?.value ?? "DETECT_AMBIGUITY");
  const [instruction, setInstruction] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [confirmSplit, setConfirmSplit] = useState(false);
  const [splitSuccess, setSplitSuccess] = useState<string[]>([]);
  const needsSources = operation !== "DETECT_AMBIGUITY";
  const splitStyle = operation === "SPLIT_FACT" || operation === "MAKE_ATOMIC";

  const job = useQuery({
    queryKey: ["ai-editorial-job", jobId],
    queryFn: () => unwrap(getAiEditorialJob({ client: contentServiceClient, path: { jobId: jobId! } })),
    enabled: Boolean(jobId),
    refetchInterval: (query) => terminal.has(query.state.data?.status ?? "") ? false : 750,
  });
  const completed = Boolean(jobId && job.data && ["COMPLETED", "PARTIALLY_COMPLETED"].includes(job.data.status));
  const proposals = useQuery({
    queryKey: ["ai-editorial-proposals", jobId],
    queryFn: () => unwrap(listAiEditorialProposals({ client: contentServiceClient, path: { jobId: jobId! } })),
    enabled: completed,
  });
  const findings = useQuery({
    queryKey: ["ai-editorial-findings", jobId],
    queryFn: () => unwrap(listAiEditorialFindings({ client: contentServiceClient, path: { jobId: jobId! } })),
    enabled: completed,
  });
  const create = useMutation({
    mutationFn: () => unwrap(createAiEditorialJob({ client: contentServiceClient, body: {
      operation, targetKnowledgeFactId: fact.id, language: "sv",
      requestedCount: splitStyle ? 5 : 1, instruction: instruction || null,
      readingPreference: null, idempotencyKey: crypto.randomUUID(),
    } })),
    onSuccess: (created) => { setJobId(created.id); setSelected([]); setSplitSuccess([]); },
  });
  const cancel = useMutation({
    mutationFn: () => unwrap(cancelAiEditorialJob({ client: contentServiceClient, path: { jobId: jobId! } })),
    onSuccess: (updated) => queryClient.setQueryData(["ai-editorial-job", jobId], updated),
  });
  const acceptSplit = useMutation({
    mutationFn: async () => unwrap(acceptAiEditorialSplit({ client: contentServiceClient, body: {
      jobId: jobId!, targetKnowledgeFactId: fact.id, targetFactVersionId: fact.currentVersionId,
      targetContentChecksum: await sha256(fact.canonicalStatement), selectedProposalIds: selected,
      acceptanceMode: "CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL", idempotencyKey: crypto.randomUUID(),
    } })),
    onSuccess: (result) => {
      setConfirmSplit(false); setSplitSuccess(result.resultingFactIds);
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.facts.all });
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.facts.detail(fact.id) });
    },
  });

  if (!enabled) return null;
  return <section className="card ai-editorial" aria-labelledby="ai-editorial-heading">
    <h2 id="ai-editorial-heading">AI editing assistant</h2>
    <p>AI output is advisory. Nothing changes until an authorized author explicitly accepts a proposal.</p>
    {!jobId && <div className="form">
      <label>Editorial operation
        <select value={operation} onChange={(event) => setOperation(event.target.value as AiEditorialOperation)}>
          {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      </label>
      <label>Optional instruction
        <textarea maxLength={1000} rows={3} value={instruction} onChange={(event) => setInstruction(event.target.value)} />
      </label>
      {needsSources && !sourcesReady && <p role="alert" className="warning">Every linked Source needs stored content before this grounded operation can run. Update the Source first, or choose Detect ambiguity for wording-only analysis.</p>}
      <button type="button" disabled={create.isPending || (needsSources && !sourcesReady)} onClick={() => create.mutate()}>
        {create.isPending ? "Starting…" : "Run editorial analysis"}
      </button>
    </div>}
    {jobId && job.data && <div aria-live="polite">
      <p><strong>Status:</strong> {job.data.status.replaceAll("_", " ")}</p>
      {["QUEUED", "RUNNING"].includes(job.data.status) && <button type="button" className="secondary" disabled={cancel.isPending} onClick={() => cancel.mutate()}>Cancel</button>}
      {job.data.status === "FAILED" && <p role="alert" className="error">{job.data.errorMessage ?? "The analysis could not be completed."}</p>}
      {terminal.has(job.data.status) && <button type="button" className="secondary" onClick={() => { setJobId(undefined); setConfirmSplit(false); }}>Run another operation</button>}
    </div>}
    {(create.error || job.error || proposals.error || findings.error || cancel.error) &&
      <p role="alert" className="error">{(create.error || job.error || proposals.error || findings.error || cancel.error)?.message}</p>}

    <div className="proposal-grid">
      {proposals.data?.map((proposal, index) => <EditorialProposalCard
        key={proposal.id} proposal={proposal} fact={fact} label={splitStyle ? `Proposed Fact ${index + 1}` : "Proposed"}
        selectable={splitStyle && canMutate} selected={selected.includes(proposal.id)}
        onSelect={(checked) => setSelected(current => checked ? [...current, proposal.id] : current.filter(id => id !== proposal.id))}
        allowSingleAccept={!splitStyle && canMutate}
        onDone={() => {
          queryClient.invalidateQueries({ queryKey: ["ai-editorial-proposals", jobId] });
          queryClient.invalidateQueries({ queryKey: adminQueryKeys.facts.detail(fact.id) });
          queryClient.invalidateQueries({ queryKey: adminQueryKeys.facts.versions(fact.id) });
        }} />)}
    </div>
    {splitStyle && selected.length > 0 && !splitSuccess.length && <button type="button" onClick={() => setConfirmSplit(true)}>Review {selected.length} selected draft{selected.length === 1 ? "" : "s"}</button>}
    {confirmSplit && <section className="card split-confirmation" role="dialog" aria-modal="true" aria-labelledby="split-confirmation-title">
      <h3 id="split-confirmation-title">Confirm split draft creation</h3>
      <p><strong>Original:</strong> {fact.canonicalStatement}</p>
      <p><strong>Learning objective:</strong> {fact.learningObjectiveTitle}</p>
      <p><strong>Selected proposed facts:</strong> {selected.length}</p>
      <p><strong>Source relationships:</strong> inherited from evidence attached to each selected proposal.</p>
      <p className="warning">Keep original and create selected drafts. The original remains unchanged. New facts remain DRAFT and UNREVIEWED and require normal human review.</p>
      <div className="actions">
        <button type="button" className="secondary" onClick={() => setConfirmSplit(false)}>Back</button>
        <button type="button" disabled={acceptSplit.isPending} onClick={() => acceptSplit.mutate()}>{acceptSplit.isPending ? "Creating drafts…" : "Create selected drafts"}</button>
      </div>
      {acceptSplit.error && <p role="alert" className="error">{acceptSplit.error.message}</p>}
    </section>}
    {splitSuccess.length > 0 && <p role="status" className="success">Created {splitSuccess.length} unreviewed draft fact{splitSuccess.length === 1 ? "" : "s"}. The original fact is unchanged.</p>}
    <div className="finding-list">
      {findings.data?.map((finding) => <EditorialFindingCard key={finding.id} finding={finding} canDismiss={canMutate || fact.reviewStatus === "UNDER_REVIEW"} onDone={() => queryClient.invalidateQueries({ queryKey: ["ai-editorial-findings", jobId] })} />)}
    </div>
    {completed && proposals.data?.length === 0 && findings.data?.length === 0 && <p role="status">No editorial issues or proposals were returned.</p>}
  </section>;
}

function EditorialProposalCard({ proposal, fact, label, selectable, selected, onSelect, allowSingleAccept, onDone }: {
  proposal: AiEditorialProposal; fact: KnowledgeFact; label: string; selectable: boolean; selected: boolean;
  onSelect: (checked: boolean) => void; allowSingleAccept: boolean; onDone: () => void;
}) {
  const [text, setText] = useState(proposal.editedText);
  const edit = useMutation({ mutationFn: () => unwrap(editAiEditorialProposal({ client: contentServiceClient, path: { proposalId: proposal.id }, body: { text, version: proposal.version } })), onSuccess: onDone });
  const reject = useMutation({ mutationFn: () => unwrap(rejectAiEditorialProposal({ client: contentServiceClient, path: { proposalId: proposal.id }, body: { reason: null, version: proposal.version } })), onSuccess: onDone });
  const accept = useMutation({ mutationFn: () => unwrap(acceptAiEditorialProposal({ client: contentServiceClient, path: { proposalId: proposal.id }, body: { version: proposal.version } })), onSuccess: onDone });
  const editable = ["PROPOSED", "EDITED"].includes(proposal.status);
  const error = edit.error || reject.error || accept.error;
  return <article className="card ai-editorial-proposal">
    <div className="actions"><span className="status-badge">{proposal.status}</span>{selectable && editable && <label className="checkbox"><input aria-label={`Select ${label}`} type="checkbox" checked={selected} disabled={text !== proposal.editedText} onChange={(event) => onSelect(event.target.checked)} /> Select for draft creation</label>}</div>
    <div className="ai-comparison"><div><h3>Original</h3><p>{fact.canonicalStatement}</p></div><div><h3>{label} · Changed</h3><textarea aria-label={`${label} text`} rows={4} disabled={!editable} value={text} onChange={(event) => { setText(event.target.value); if (selected) onSelect(false); }} /></div></div>
    <p><strong>Why this change:</strong> {proposal.rationale}</p>
    {typeof proposal.coverage.summary === "string" && <p><strong>Coverage:</strong> {proposal.coverage.summary}</p>}
    <h4>Evidence</h4>{proposal.sourceEvidence.map((evidence, index) => <blockquote key={`${evidence.sourceId}-${index}`}>“{evidence.quote}”<footer>Stored Source {evidence.sourceId}</footer></blockquote>)}
    {proposal.warnings.length > 0 && <p className="warning"><strong>Warning:</strong> {proposal.warnings.join("; ")}</p>}
    {editable && <div className="actions"><button type="button" className="secondary" disabled={edit.isPending || text === proposal.editedText} onClick={() => edit.mutate()}>Save edit</button><button type="button" className="secondary" disabled={reject.isPending} onClick={() => reject.mutate()}>Reject</button>{allowSingleAccept && <button type="button" disabled={accept.isPending || text !== proposal.editedText} onClick={() => accept.mutate()}>Accept change</button>}</div>}
    {text !== proposal.editedText && <p className="warning">Save your edit before selecting or accepting this proposal.</p>}
    {error && <p role="alert" className="error">{error.message}</p>}
  </article>;
}

function EditorialFindingCard({ finding, canDismiss, onDone }: { finding: AiEditorialFinding; canDismiss: boolean; onDone: () => void }) {
  const dismiss = useMutation({ mutationFn: () => unwrap(dismissAiEditorialFinding({ client: contentServiceClient, path: { findingId: finding.id }, body: { reason: null, version: finding.version } })), onSuccess: onDone });
  const lists = ["supportedFragments", "unsupportedFragments", "missingQualifiers", "strengths", "concerns"] as const;
  return <article className="card editorial-finding">
    <div className="actions"><span className="status-badge">{finding.severity}</span><span className="status-badge">{finding.status}</span></div>
    <h3>{finding.title}</h3><p>{finding.message}</p>
    {finding.affectedText && <p><strong>Affected phrase:</strong> “{finding.affectedText}”</p>}
    {typeof finding.metadata.supportStatus === "string" && <p><strong>Support status:</strong> {finding.metadata.supportStatus.replaceAll("_", " ")}</p>}
    {lists.map((key) => Array.isArray(finding.metadata[key]) && (finding.metadata[key] as unknown[]).length > 0 ? <div key={key}><h4>{readable(key)}</h4><ul>{(finding.metadata[key] as unknown[]).map((item, index) => <li key={index}>{String(item)}</li>)}</ul></div> : null)}
    {finding.sourceEvidence.length > 0 && <><h4>Evidence</h4>{finding.sourceEvidence.map((evidence, index) => <blockquote key={`${evidence.sourceId}-${index}`}>“{evidence.quote}”<footer>Stored Source {evidence.sourceId}</footer></blockquote>)}</>}
    {finding.suggestedAction && <p><strong>Recommended next human action:</strong> {finding.suggestedAction.replaceAll("_", " ")}</p>}
    <p><small>AI-assisted editorial analysis; not a validation or reviewer decision.</small></p>
    {canDismiss && finding.status === "OPEN" && <button type="button" className="secondary" disabled={dismiss.isPending} onClick={() => dismiss.mutate()}>Dismiss finding</button>}
    {dismiss.error && <p role="alert" className="error">{dismiss.error.message}</p>}
  </article>;
}

function readable(value: string) { return value.replace(/([A-Z])/g, " $1").replace(/^./, first => first.toUpperCase()); }
async function sha256(value: string) {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest)).map(byte => byte.toString(16).padStart(2, "0")).join("");
}
