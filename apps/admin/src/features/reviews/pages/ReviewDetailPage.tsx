import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import {
  addReviewComment,
  approveKnowledgeFact,
  approveQuestion,
  assignReview,
  changeReviewPriority,
  claimReview,
  getReview,
  rejectKnowledgeFact,
  rejectQuestion,
  requireKnowledgeFactUpdate,
  requireQuestionUpdate,
  unclaimReview,
  type ReviewPriority,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";
import { AsyncState } from "../../../components/AsyncState";
import { ReviewStatusBadge } from "../components/ReviewStatusBadge";
import { ReviewHistoryTimeline } from "../components/ReviewHistoryTimeline";
import { useAuth } from "../../../app/auth/AuthContext";
import { AdminRole } from "../../../app/permissions/permissions";

type Content = {
  version: number;
  questionText?: string;
  questionType?: string;
  difficulty?: string;
  options?: Array<{
    id: string;
    text: string;
    correct: boolean;
    feedback?: string | null;
  }>;
  explanation?: string | null;
  canonicalStatement?: string;
  validFrom?: string | null;
  validTo?: string | null;
  knowledgeFacts?: Array<{ id: string; canonicalStatement: string }>;
};
const reasons = [
  "FACTUALLY_INCORRECT",
  "INSUFFICIENT_SOURCE_SUPPORT",
  "OUTDATED_SOURCE",
  "AMBIGUOUS_WORDING",
  "POOR_DISTRACTOR_QUALITY",
  "MULTIPLE_PLAUSIBLE_ANSWERS",
  "INCORRECT_CORRECT_ANSWER",
  "INVALID_DIFFICULTY",
  "DUPLICATE_CONTENT",
  "OUT_OF_SCOPE",
  "LEGAL_OR_POLICY_CONCERN",
  "FORMATTING_OR_LANGUAGE",
  "OTHER",
];
export function ReviewDetailPage() {
  const { id } = useParams();
  const { admin } = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [decision, setDecision] = useState<"reject" | "update" | null>(null);
  const [reasonCode, setReasonCode] = useState("");
  const [reason, setReason] = useState("");
  const [comment, setComment] = useState("");
  const [assignee, setAssignee] = useState("");
  const query = useQuery({
    queryKey: adminQueryKeys.reviews.detail(id!),
    queryFn: () =>
      unwrap(
        getReview({
          client: contentServiceClient,
          path: { reviewItemId: id! },
        }),
      ),
  });
  const refresh = (data?: unknown) => {
    if (data) qc.setQueryData(adminQueryKeys.reviews.detail(id!), data);
    qc.invalidateQueries({ queryKey: adminQueryKeys.reviews.all });
    qc.invalidateQueries({ queryKey: adminQueryKeys.facts.all });
    qc.invalidateQueries({ queryKey: adminQueryKeys.questions.all });
  };
  const assignment = useMutation({
    mutationFn: (kind: "claim" | "unclaim") => {
      const d = query.data!;
      const o = {
        client: contentServiceClient,
        path: { reviewItemId: id! },
        body: { version: d.version },
      };
      return unwrap(kind === "claim" ? claimReview(o) : unclaimReview(o));
    },
    onSuccess: refresh,
  });
  const adminAction = useMutation({
    mutationFn: ([kind, value]: ["assign" | "priority", string]) =>
      kind === "assign"
        ? unwrap(
            assignReview({
              client: contentServiceClient,
              path: { reviewItemId: id! },
              body: { version: query.data!.version, assignedReviewerId: value },
            }),
          )
        : unwrap(
            changeReviewPriority({
              client: contentServiceClient,
              path: { reviewItemId: id! },
              body: {
                version: query.data!.version,
                priority: value as ReviewPriority,
              },
            }),
          ),
    onSuccess: refresh,
  });
  const addComment = useMutation({
    mutationFn: () =>
      unwrap(
        addReviewComment({
          client: contentServiceClient,
          path: { reviewItemId: id! },
          body: { version: query.data!.version, body: comment },
        }),
      ),
    onSuccess: (data) => {
      setComment("");
      refresh(data);
    },
  });
  const decide = useMutation({
    mutationFn: async (kind: "approve" | "reject" | "update") => {
      const d = query.data!;
      const content = d.content as Content;
      const options = {
        client: contentServiceClient,
        path:
          d.contentType === "QUESTION"
            ? { questionId: d.contentId }
            : { knowledgeFactId: d.contentId },
        body: {
          version: content.version,
          reason: reason || null,
          reasonCode: reasonCode || null,
        },
      };
      if (d.contentType === "QUESTION")
        return unwrap(
          kind === "approve"
            ? approveQuestion(options as Parameters<typeof approveQuestion>[0])
            : kind === "reject"
              ? rejectQuestion(options as Parameters<typeof rejectQuestion>[0])
              : requireQuestionUpdate(
                  options as Parameters<typeof requireQuestionUpdate>[0],
                ),
        );
      return unwrap(
        kind === "approve"
          ? approveKnowledgeFact(
              options as Parameters<typeof approveKnowledgeFact>[0],
            )
          : kind === "reject"
            ? rejectKnowledgeFact(
                options as Parameters<typeof rejectKnowledgeFact>[0],
              )
            : requireKnowledgeFactUpdate(
                options as Parameters<typeof requireKnowledgeFactUpdate>[0],
              ),
      );
    },
    onSuccess: () => {
      refresh();
      navigate("/reviews");
    },
    onError: () =>
      qc.invalidateQueries({ queryKey: adminQueryKeys.reviews.detail(id!) }),
  });
  const data = query.data;
  const content = data?.content as Content | undefined;
  const reviewer = admin?.roles.some(
    (r) => r === AdminRole.Admin || r === AdminRole.ContentReviewer,
  );
  const selfReview = data?.authorId === admin?.id;
  return (
    <AsyncState loading={query.isPending} error={query.error}>
      {data && (
        <>
          <header className="page-header">
            <div>
              <h1>
                {data.contentType === "QUESTION"
                  ? "Question review"
                  : "Knowledge fact review"}
              </h1>
              <p>
                <ReviewStatusBadge status={data.reviewStatus} /> ·{" "}
                {data.lifecycleStatus} · {data.priority} priority
              </p>
            </div>
          </header>
          <section className="card">
            <h2>{data.titleOrSummary}</h2>
            <p>
              Author: {data.authorName} · Submitted{" "}
              {new Date(data.submittedAt).toLocaleString()}
            </p>
            <p>
              {data.subjectName} / {data.topicName} /{" "}
              {data.learningObjectiveTitle}
            </p>
            <p>Assignment: {data.assignedReviewerName ?? "Unassigned"}</p>
            {data.impactWarningCount > 0 && (
              <p className="warning" role="status">
                Impact warning: {data.impactWarningCount} linked question(s) may
                be affected.
              </p>
            )}
          </section>
          {data.contentType === "KNOWLEDGE_FACT" ? (
            <section className="card">
              <h2>Fact and source context</h2>
              <blockquote>{content?.canonicalStatement}</blockquote>
              <p>
                Validity: {content?.validFrom ?? "open"} –{" "}
                {content?.validTo ?? "open"}
              </p>
              <ul>
                {data.sourceContext?.map((s, i) => (
                  <li key={i}>
                    <a
                      href={String(s.url ?? "#")}
                      target="_blank"
                      rel="noreferrer"
                    >
                      {String(s.publisher)} — {String(s.title)}
                    </a>{" "}
                    · {String(s.reviewStatus)} / {String(s.status)}
                  </li>
                ))}
              </ul>
            </section>
          ) : (
            <section className="card">
              <h2>Learner preview</h2>
              <h3>{content?.questionText}</h3>
              <p>
                {content?.questionType} · {content?.difficulty}
              </p>
              <ul>
                {content?.options?.map((o) => (
                  <li key={o.id}>
                    <strong>{o.correct ? "Correct: " : ""}</strong>
                    {o.text}
                    {o.feedback && <small> — {o.feedback}</small>}
                  </li>
                ))}
              </ul>
              <p>{content?.explanation}</p>
              <h3>Linked facts and sources</h3>
              <ul>
                {data.factSourceContext?.map((f, i) => (
                  <li key={i}>
                    {String(f.canonicalStatement)} · Fact{" "}
                    {String(f.reviewStatus)}/{String(f.status)} · Source{" "}
                    {String(f.publisher ?? "none")}{" "}
                    {String(f.sourceStatus ?? "")}
                  </li>
                ))}
              </ul>
            </section>
          )}
          <section className="card">
            <h2>Assignment and priority</h2>
            <div className="actions">
              {reviewer && !selfReview && !data.assignedReviewerId && (
                <button onClick={() => assignment.mutate("claim")}>
                  Claim review
                </button>
              )}
              {data.assignedReviewerId === admin?.id && (
                <button onClick={() => assignment.mutate("unclaim")}>
                  Unclaim review
                </button>
              )}
              {admin?.roles.includes(AdminRole.Admin) && (
                <>
                  <label>
                    Assign reviewer
                    <input
                      value={assignee}
                      onChange={(e) => setAssignee(e.target.value)}
                    />
                  </label>
                  <button
                    disabled={!assignee}
                    onClick={() => adminAction.mutate(["assign", assignee])}
                  >
                    Assign
                  </button>
                  <label>
                    Priority
                    <select
                      value={data.priority}
                      onChange={(e) =>
                        adminAction.mutate(["priority", e.target.value])
                      }
                    >
                      <option>LOW</option>
                      <option>NORMAL</option>
                      <option>HIGH</option>
                      <option>URGENT</option>
                    </select>
                  </label>
                </>
              )}
            </div>
          </section>
          <section className="card">
            <h2>Reviewer comments</h2>
            {data.comments.length === 0 ? (
              <p>No comments yet.</p>
            ) : (
              <ul>
                {data.comments.map((c) => (
                  <li key={c.id}>
                    <strong>{c.authorName}</strong> ·{" "}
                    {new Date(c.createdAt).toLocaleString()}
                    <p>{c.body}</p>
                    <small>Version {c.contentVersionId}</small>
                  </li>
                ))}
              </ul>
            )}
            {reviewer && (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  addComment.mutate();
                }}
              >
                <label>
                  New comment
                  <textarea
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                    required
                  />
                </label>
                <button disabled={!comment.trim()}>Add comment</button>
              </form>
            )}
          </section>
          {reviewer && data.reviewStatus === "UNDER_REVIEW" && (
            <section className="card">
              <h2>Review decision</h2>
              {selfReview ? (
                <p className="warning">
                  You cannot review content you authored.
                </p>
              ) : (
                <>
                  <button
                    onClick={() =>
                      confirm("Approve this reviewed version?") &&
                      decide.mutate("approve")
                    }
                  >
                    Approve
                  </button>
                  <button onClick={() => setDecision("reject")}>Reject</button>
                  <button onClick={() => setDecision("update")}>
                    Require update
                  </button>
                  {decision && (
                    <form
                      onSubmit={(e) => {
                        e.preventDefault();
                        decide.mutate(decision);
                      }}
                    >
                      <label>
                        Reason code
                        <select
                          value={reasonCode}
                          onChange={(e) => setReasonCode(e.target.value)}
                          required
                        >
                          <option value="">Select a reason</option>
                          {reasons.map((r) => (
                            <option key={r}>{r}</option>
                          ))}
                        </select>
                      </label>
                      <label>
                        Reviewer feedback
                        <textarea
                          value={reason}
                          onChange={(e) => setReason(e.target.value)}
                          required
                          aria-describedby="feedback-help"
                        />
                      </label>
                      <small id="feedback-help">
                        Explain what must change so the author can revise it.
                      </small>
                      <button disabled={!reasonCode || !reason.trim()}>
                        Confirm{" "}
                        {decision === "reject"
                          ? "rejection"
                          : "required update"}
                      </button>
                      <button type="button" onClick={() => setDecision(null)}>
                        Cancel
                      </button>
                    </form>
                  )}
                </>
              )}
            </section>
          )}
          <section className="card">
            <h2>Review history</h2>
            <ReviewHistoryTimeline items={data.history} />
          </section>
          {(assignment.error ||
            adminAction.error ||
            addComment.error ||
            decide.error) && (
            <p role="alert" className="error">
              {
                (
                  assignment.error ||
                  adminAction.error ||
                  addComment.error ||
                  decide.error
                )?.message
              }{" "}
              The item may have changed; reload and try again.
            </p>
          )}
        </>
      )}
    </AsyncState>
  );
}
