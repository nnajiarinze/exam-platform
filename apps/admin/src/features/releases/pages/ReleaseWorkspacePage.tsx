/* Release selections are local editable copies of the loaded immutable IDs. */
/* eslint-disable react-hooks/set-state-in-effect */
import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  activateRelease,
  deleteRelease,
  deliverRelease,
  getRelease,
  listEligibleReleaseFacts,
  listEligibleReleaseQuestions,
  previewRelease,
  publishRelease,
  replaceReleaseSelection,
  retireRelease,
  retryReleaseDelivery,
  validateRelease,
  type EligibleContentItem,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap, unwrapVoid } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";
import { AsyncState } from "../../../components/AsyncState";
import { SafeDeleteDialog } from "../../../components/SafeDeleteDialog";
import { useAuth } from "../../../app/auth/AuthContext";
import { can, Permission } from "../../../app/permissions/permissions";

type ReleaseItem = { contentType?: string; contentId?: string };
export function ReleaseWorkspacePage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { admin } = useAuth();
  const [questionIds, setQuestionIds] = useState<string[]>([]);
  const [factIds, setFactIds] = useState<string[]>([]);
  const [report, setReport] = useState<{
    valid: boolean;
    errors: Array<{ code: string; message: string }>;
    warnings: Array<{ code: string; message: string }>;
  } | null>(null);
  const detail = useQuery({
    queryKey: adminQueryKeys.releases.detail(id),
    queryFn: () =>
      unwrap(
        getRelease({ client: contentServiceClient, path: { releaseId: id } }),
      ),
    enabled: !!id,
  });
  const eligible = useQuery({
    queryKey: adminQueryKeys.releases.eligible(id),
    queryFn: async () => {
      const [q, f] = await Promise.all([
        unwrap(
          listEligibleReleaseQuestions({
            client: contentServiceClient,
            path: { releaseId: id },
          }),
        ),
        unwrap(
          listEligibleReleaseFacts({
            client: contentServiceClient,
            path: { releaseId: id },
          }),
        ),
      ]);
      return { questions: q.items, facts: f.items };
    },
    enabled: !!id && detail.data?.status === "DRAFT",
  });
  const preview = useQuery({
    queryKey: ["release-preview", id],
    queryFn: () =>
      unwrap(
        previewRelease({
          client: contentServiceClient,
          path: { releaseId: id },
        }),
      ),
    enabled: !!id,
  });
  useEffect(() => {
    if (!detail.data) return;
    const items = detail.data.items as ReleaseItem[];
    setQuestionIds(
      items
        .filter((x) => x.contentType === "QUESTION")
        .map((x) => x.contentId!),
    );
    setFactIds(
      items
        .filter(
          (x) =>
            x.contentType === "KNOWLEDGE_FACT" &&
            !items.some(
              (q) =>
                q.contentType === "QUESTION" && q.contentId === x.contentId,
            ),
        )
        .map((x) => x.contentId!),
    );
  }, [detail.data]);
  const refresh = () =>
    qc.invalidateQueries({ queryKey: adminQueryKeys.releases.detail(id) });
  const action = useMutation({
    mutationFn: async (kind: string) => {
      const version = detail.data!.version;
      if (kind === "select")
        return unwrap(
          replaceReleaseSelection({
            client: contentServiceClient,
            path: { releaseId: id },
            body: { questionIds, factIds, version },
          }),
        );
      if (kind === "validate")
        return unwrap(
          validateRelease({
            client: contentServiceClient,
            path: { releaseId: id },
            body: { version },
          }),
        );
      if (kind === "publish")
        return unwrap(
          publishRelease({
            client: contentServiceClient,
            path: { releaseId: id },
            body: { version },
          }),
        );
      if (kind === "deliver")
        return unwrap(
          deliverRelease({
            client: contentServiceClient,
            path: { releaseId: id },
          }),
        );
      if (kind === "retry")
        return unwrap(
          retryReleaseDelivery({
            client: contentServiceClient,
            path: { releaseId: id },
          }),
        );
      if (kind === "activate")
        return unwrap(
          activateRelease({
            client: contentServiceClient,
            path: { releaseId: id },
          }),
        );
      return unwrap(
        retireRelease({
          client: contentServiceClient,
          path: { releaseId: id },
          body: { version },
        }),
      );
    },
    onSuccess: (data, kind) => {
      if (kind === "validate" && "valid" in data) setReport(data);
      refresh();
      qc.invalidateQueries({ queryKey: ["release-preview", id] });
    },
  });
  if (!id) return null;
  return (
    <AsyncState loading={detail.isPending} error={detail.error}>
      {detail.data && (
        <>
          <header className="page-header">
            <span className="eyebrow">
              {detail.data.examName} · {detail.data.examVersionName}
            </span>
            <h1>{detail.data.displayName}</h1>
            <p>
              {detail.data.releaseNumber} ·{" "}
              <strong>{detail.data.status}</strong>
            </p>
          </header>
          <div className="actions">
            <Link to="/releases">Back to releases</Link>
            {detail.data.status === "DRAFT" && (
              <>
                <button onClick={() => action.mutate("select")}>
                  Save selection
                </button>
                <button onClick={() => action.mutate("validate")}>
                  Validate
                </button>
                {can(admin, Permission.PublishRelease) && (
                  <SafeDeleteDialog
                    entityName={detail.data.displayName}
                    entityLabel="Release"
                    onDelete={(reason) =>
                      unwrapVoid(
                        deleteRelease({
                          client: contentServiceClient,
                          path: { releaseId: id },
                          query: { reason: reason || undefined },
                        }),
                      )
                    }
                    onDeleted={() => {
                      qc.invalidateQueries({
                        queryKey: adminQueryKeys.releases.all,
                      });
                      navigate("/releases");
                    }}
                  />
                )}
              </>
            )}
            {detail.data.status === "VALIDATED" && (
              <button onClick={() => action.mutate("publish")}>
                Publish immutable snapshot
              </button>
            )}
            {detail.data.status === "PUBLISHED" && (
              <button onClick={() => action.mutate("deliver")}>
                Deliver to Learning Service
              </button>
            )}
            {detail.data.status === "DELIVERY_FAILED" && (
              <button onClick={() => action.mutate("retry")}>
                Retry delivery
              </button>
            )}
            {detail.data.status === "DELIVERED" && (
              <button onClick={() => action.mutate("activate")}>
                Activate for learners
              </button>
            )}
            {!["ACTIVE", "RETIRED", "DRAFT", "VALIDATED"].includes(
              detail.data.status,
            ) && (
              <button onClick={() => action.mutate("retire")}>Retire</button>
            )}
          </div>
          {action.error && <p className="error">{action.error.message}</p>}
          <div className="dashboard-grid release-grid">
            <section className="card">
              <h2>Release contents</h2>
              <p>
                {detail.data.questionCount} questions ·{" "}
                {detail.data.knowledgeFactCount} facts
              </p>
              {detail.data.status === "DRAFT" ? (
                <>
                  <ContentPicker
                    title="Approved questions"
                    items={eligible.data?.questions ?? []}
                    selected={questionIds}
                    setSelected={setQuestionIds}
                  />
                  <ContentPicker
                    title="Additional approved facts"
                    items={eligible.data?.facts ?? []}
                    selected={factIds}
                    setSelected={setFactIds}
                  />
                </>
              ) : (
                <p>The exact content versions are frozen.</p>
              )}
            </section>
            <section className="card">
              <h2>Coverage</h2>
              <Json value={detail.data.coverage} />
              <h2>Changes from previous release</h2>
              <Json value={detail.data.diff} />
            </section>
            <section className="card span-two">
              <h2>Validation</h2>
              {report ? (
                <>
                  <p className={report.valid ? "connected" : "error"}>
                    {report.valid ? "Validation passed" : "Validation failed"}
                  </p>
                  {[...report.errors, ...report.warnings].map((x, i) => (
                    <p key={`${x.code}-${i}`}>
                      <strong>{x.code}</strong>: {x.message}
                    </p>
                  ))}
                </>
              ) : (
                <p>Run validation after saving the selection.</p>
              )}
            </section>
            <section className="card span-two">
              <h2>Snapshot preview</h2>
              <Json value={preview.data} />
            </section>
            <section className="card span-two">
              <h2>Delivery history</h2>
              {detail.data.deliveryAttempts.length === 0 ? (
                <p>No delivery attempts.</p>
              ) : (
                <table>
                  <thead>
                    <tr>
                      <th>Attempt</th>
                      <th>Status</th>
                      <th>Time</th>
                      <th>Result</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.data.deliveryAttempts.map((x) => (
                      <tr key={x.id}>
                        <td>{x.attemptNumber}</td>
                        <td>{x.status}</td>
                        <td>{new Date(x.startedAt).toLocaleString()}</td>
                        <td>{x.errorMessage ?? x.responseCode ?? "Success"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
          </div>
        </>
      )}
    </AsyncState>
  );
}

function ContentPicker({
  title,
  items,
  selected,
  setSelected,
}: {
  title: string;
  items: EligibleContentItem[];
  selected: string[];
  setSelected: (ids: string[]) => void;
}) {
  return (
    <fieldset>
      <legend>{title}</legend>
      {items.length === 0 ? (
        <p>No eligible content.</p>
      ) : (
        items.map((x) => (
          <label className="release-choice" key={x.id}>
            <input
              type="checkbox"
              checked={selected.includes(x.id)}
              onChange={(e) =>
                setSelected(
                  e.target.checked
                    ? [...selected, x.id]
                    : selected.filter((id) => id !== x.id),
                )
              }
            />
            <span>
              {x.text}
              <small>
                {x.subjectName} · {x.topicName} · {x.learningObjectiveTitle}
              </small>
            </span>
          </label>
        ))
      )}
    </fieldset>
  );
}
function Json({ value }: { value: unknown }) {
  return (
    <pre className="json-preview">{JSON.stringify(value ?? {}, null, 2)}</pre>
  );
}
