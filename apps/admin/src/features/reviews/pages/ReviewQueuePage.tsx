import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  claimReview,
  getReviewSummary,
  listReviews,
  unclaimReview,
  type FactReviewStatus,
  type FactStatus,
  type ReviewContentType,
  type ReviewPriority,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";
import { AsyncState } from "../../../components/AsyncState";
import { ReviewStatusBadge } from "../components/ReviewStatusBadge";
import { useAuth } from "../../../app/auth/AuthContext";
import { EmptyState, MetricCard, PageHeader, StatusBadge, TableFrame } from "../../../components/AdminUi";

export function ReviewQueuePage() {
  const { admin } = useAuth();
  const qc = useQueryClient();
  const [search, setSearch] = useState("");
  const [type, setType] = useState("");
  const [review, setReview] = useState("UNDER_REVIEW");
  const [lifecycle, setLifecycle] = useState("");
  const [priority, setPriority] = useState("");
  const [mine, setMine] = useState(false);
  const [unassigned, setUnassigned] = useState(false);
  const [warnings, setWarnings] = useState(false);
  const [page, setPage] = useState(0);
  const filters = {
    search,
    type,
    review,
    lifecycle,
    priority,
    mine,
    unassigned,
    warnings,
    page,
  };
  const queue = useQuery({
    queryKey: adminQueryKeys.reviews.list(filters),
    queryFn: () =>
      unwrap(
        listReviews({
          client: contentServiceClient,
          query: {
            page,
            size: 20,
            search: search || undefined,
            contentType: (type || undefined) as ReviewContentType | undefined,
            reviewStatus: (review || undefined) as FactReviewStatus | undefined,
            lifecycleStatus: (lifecycle || undefined) as FactStatus | undefined,
            priority: (priority || undefined) as ReviewPriority | undefined,
            assignedToMe: mine || undefined,
            unassignedOnly: unassigned || undefined,
            hasImpactWarnings: warnings || undefined,
          },
        }),
      ),
  });
  const summary = useQuery({
    queryKey: adminQueryKeys.reviews.summary,
    queryFn: () => unwrap(getReviewSummary({ client: contentServiceClient })),
  });
  const assignment = useMutation({
    mutationFn: (value: {
      id: string;
      version: number;
      kind: "claim" | "unclaim";
    }) =>
      unwrap(
        value.kind === "claim"
          ? claimReview({
              client: contentServiceClient,
              path: { reviewItemId: value.id },
              body: { version: value.version },
            })
          : unclaimReview({
              client: contentServiceClient,
              path: { reviewItemId: value.id },
              body: { version: value.version },
            }),
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.reviews.all });
    },
  });
  return (
    <>
      <PageHeader eyebrow="Review queue" title="Reviews" description="Review submitted knowledge facts and questions while preserving separation of duties." />
      <section className="report-grid" aria-label="Review summary">
        <MetricCard label="Awaiting review" value={summary.data?.awaitingReview ?? "—"} tone="blue" />
        <MetricCard label="Assigned to me" value={summary.data?.assignedToMe ?? "—"} tone="green" />
        <MetricCard label="Unassigned" value={summary.data?.unassigned ?? "—"} tone="neutral" />
        <MetricCard label="High priority" value={summary.data?.highPriority ?? "—"} tone="yellow" />
      </section>
      <div className="filters">
        <label>
          Search
          <input
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          Type
          <select value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">All</option>
            <option>KNOWLEDGE_FACT</option>
            <option>QUESTION</option>
          </select>
        </label>
        <label>
          Review status
          <select value={review} onChange={(e) => setReview(e.target.value)}>
            <option value="ALL">All history</option>
            <option>UNDER_REVIEW</option>
            <option>REQUIRES_UPDATE</option>
            <option>REJECTED</option>
            <option>APPROVED</option>
          </select>
        </label>
        <label>
          Lifecycle
          <select
            value={lifecycle}
            onChange={(e) => setLifecycle(e.target.value)}
          >
            <option value="">All</option>
            <option>DRAFT</option>
            <option>ACTIVE</option>
            <option>RETIRED</option>
          </select>
        </label>
        <label>
          Priority
          <select
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
          >
            <option value="">All</option>
            <option>URGENT</option>
            <option>HIGH</option>
            <option>NORMAL</option>
            <option>LOW</option>
          </select>
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={mine}
            onChange={(e) => {
              setMine(e.target.checked);
              setUnassigned(false);
            }}
          />
          <span>Assigned to me</span>
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={unassigned}
            onChange={(e) => {
              setUnassigned(e.target.checked);
              setMine(false);
            }}
          />
          <span>Unassigned only</span>
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={warnings}
            onChange={(e) => setWarnings(e.target.checked)}
          />
          <span>Has impact warnings</span>
        </label>
      </div>
      <AsyncState loading={queue.isPending} error={queue.error}>
        {queue.data?.items.length === 0 ? (
          <EmptyState title="No review items match these filters" description="Change the filters to see other review work." />
        ) : (
          <TableFrame><table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Content</th>
                <th>Subject / topic</th>
                <th>Author</th>
                <th>Submitted</th>
                <th>Priority</th>
                <th>Status</th>
                <th>Assignment</th>
                <th>Warnings</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {queue.data?.items.map((item) => (
                <tr key={item.id}>
                  <td>{item.contentType.replaceAll("_", " ")}</td>
                  <td>
                    <Link to={`/reviews/${item.id}`}>
                      {item.titleOrSummary}
                    </Link>
                    <br />
                    <small>{item.contentCode}</small>
                  </td>
                  <td>
                    {item.subjectName} / {item.topicName}
                  </td>
                  <td>{item.authorName}</td>
                  <td>{new Date(item.submittedAt).toLocaleDateString()}</td>
                  <td><StatusBadge value={item.priority} /></td>
                  <td>
                    <ReviewStatusBadge status={item.reviewStatus} />
                  </td>
                  <td>{item.assignedReviewerName ?? "Unassigned"}</td>
                  <td>{item.impactWarningCount || "—"}</td>
                  <td>
                    <Link className="button" to={`/reviews/${item.id}`}>
                      Open
                    </Link>
                    {item.reviewStatus === "UNDER_REVIEW" &&
                      !item.assignedReviewerId &&
                      item.authorId !== admin?.id && (
                        <button
                          onClick={() =>
                            assignment.mutate({
                              id: item.id,
                              version: item.version,
                              kind: "claim",
                            })
                          }
                        >
                          Claim
                        </button>
                      )}
                    {item.assignedReviewerId === admin?.id && (
                      <button
                        onClick={() =>
                          assignment.mutate({
                            id: item.id,
                            version: item.version,
                            kind: "unclaim",
                          })
                        }
                      >
                        Unclaim
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table></TableFrame>
        )}
        <div className="pager">
          <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </button>
          <span>Page {page + 1}</span>
          <button
            disabled={!queue.data || page + 1 >= queue.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
        {assignment.error && (
          <p role="alert" className="error">
            {assignment.error.message} Reload the queue and try again.
          </p>
        )}
      </AsyncState>
    </>
  );
}
