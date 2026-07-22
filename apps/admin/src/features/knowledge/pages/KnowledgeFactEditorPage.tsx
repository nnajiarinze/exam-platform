import { useEffect } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import {
  approveKnowledgeFact,
  createKnowledgeFact,
  deleteKnowledgeFact,
  getKnowledgeFact,
  getKnowledgeFactAiProvenance,
  listKnowledgeFactVersions,
  listLearningObjectives,
  listSources,
  rejectKnowledgeFact,
  requireKnowledgeFactUpdate,
  retireKnowledgeFact,
  submitKnowledgeFact,
  updateKnowledgeFact,
  type KnowledgeFactRequest,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap, unwrapVoid } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";
import { AsyncState } from "../../../components/AsyncState";
import { useUnsavedWarning } from "../../../hooks/useUnsavedWarning";
import { useAuth } from "../../../app/auth/AuthContext";
import { can, Permission } from "../../../app/permissions/permissions";
import { SafeDeleteDialog } from "../../../components/SafeDeleteDialog";
import { AiEditorialWorkspace } from "../../ai/components/AiEditorialWorkspace";
import { AiQuestionGenerationWorkspace } from "../../ai/components/AiQuestionGenerationWorkspace";
import { validateEditorialInput } from "../../ai/editorialInputValidation";

export function KnowledgeFactEditorPage() {
  const { id } = useParams();
  const edit = Boolean(id);
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { admin } = useAuth();
  const fact = useQuery({
    queryKey: adminQueryKeys.facts.detail(id ?? "new"),
    queryFn: () =>
      unwrap(
        getKnowledgeFact({
          client: contentServiceClient,
          path: { knowledgeFactId: id! },
        }),
      ),
    enabled: edit,
  });
  const objectives = useQuery({
    queryKey: adminQueryKeys.objectives.list({ picker: true }),
    queryFn: () =>
      unwrap(
        listLearningObjectives({
          client: contentServiceClient,
          query: { page: 0, size: 100 },
        }),
      ),
  });
  const sources = useQuery({
    queryKey: adminQueryKeys.sources.list({ picker: true }),
    queryFn: () =>
      unwrap(
        listSources({
          client: contentServiceClient,
          query: { page: 0, size: 100, status: "ACTIVE" },
        }),
      ),
  });
  const versions = useQuery({
    queryKey: adminQueryKeys.facts.versions(id ?? "new"),
    queryFn: () =>
      unwrap(
        listKnowledgeFactVersions({
          client: contentServiceClient,
          path: { knowledgeFactId: id! },
        }),
      ),
    enabled: edit,
  });
  const provenance = useQuery({
    queryKey: ["knowledge-facts", id, "ai-provenance"],
    queryFn: () => unwrap(getKnowledgeFactAiProvenance({ client: contentServiceClient, path: { knowledgeFactId: id! } })),
    enabled: edit,
    retry: false,
  });
  const form = useForm<KnowledgeFactRequest>({
    defaultValues: {
      learningObjectiveId: "",
      canonicalStatement: "",
      validFrom: null,
      validTo: null,
      sourceIds: [],
    },
  });
  useEffect(() => {
    if (fact.data)
      form.reset({
        learningObjectiveId: fact.data.learningObjectiveId,
        canonicalStatement: fact.data.canonicalStatement,
        validFrom: fact.data.validFrom ?? null,
        validTo: fact.data.validTo ?? null,
        sourceIds: fact.data.sourceIds,
      });
  }, [fact.data, form]);
  useUnsavedWarning(form.formState.isDirty);
  const save = useMutation({
    mutationFn: (body: KnowledgeFactRequest) =>
      edit
        ? unwrap(
            updateKnowledgeFact({
              client: contentServiceClient,
              path: { knowledgeFactId: id! },
              body: {
                ...body,
                validFrom: body.validFrom || null,
                validTo: body.validTo || null,
                version: fact.data!.version,
              },
            }),
          )
        : unwrap(
            createKnowledgeFact({
              client: contentServiceClient,
              body: {
                ...body,
                validFrom: body.validFrom || null,
                validTo: body.validTo || null,
              },
            }),
          ),
    onSuccess: (item) => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.facts.all });
      qc.setQueryData(adminQueryKeys.facts.detail(item.id), item);
      qc.invalidateQueries({ queryKey: adminQueryKeys.facts.versions(item.id) });
      navigate(`/knowledge/facts/${item.id}`);
    },
  });
  const action = useMutation({
    mutationFn: async (
      kind: "submit" | "approve" | "reject" | "update" | "retire",
    ) => {
      if (!fact.data) throw new Error("Fact is not loaded");
      const reason =
        kind === "reject" || kind === "update"
          ? prompt("Reason")?.trim()
          : undefined;
      if ((kind === "reject" || kind === "update") && !reason)
        throw new Error("A reason is required");
      const options = {
        client: contentServiceClient,
        path: { knowledgeFactId: id! },
        body: { version: fact.data.version, reason: reason ?? null },
      };
      return unwrap(
        kind === "submit"
          ? submitKnowledgeFact(options)
          : kind === "approve"
            ? approveKnowledgeFact(options)
            : kind === "reject"
              ? rejectKnowledgeFact(options)
              : kind === "update"
                ? requireKnowledgeFactUpdate(options)
                : retireKnowledgeFact(options),
      );
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.facts.detail(id!) });
      qc.invalidateQueries({ queryKey: adminQueryKeys.facts.versions(id!) });
    },
  });
  const statement = useWatch({
    control: form.control,
    name: "canonicalStatement",
  });
  const statementValidation = validateEditorialInput(statement ?? "");
  const reviewer = can(admin, Permission.ReviewContent);
  const author = can(admin, Permission.EditDraftContent);
  return (
    <>
      <header className="page-header">
        <div>
          <h1>{edit ? "Knowledge fact" : "Create knowledge fact"}</h1>
          {fact.data && (
            <p>
              {fact.data.reviewStatus} · {fact.data.status} · Version{" "}
              {fact.data.version}
            </p>
          )}
        </div>
      </header>
      <AsyncState
        loading={
          (edit && fact.isPending) || objectives.isPending || sources.isPending
        }
        error={fact.error || objectives.error || sources.error}
      >
        <form
          className="form"
          onSubmit={form.handleSubmit((value) => save.mutate(value))}
        >
          <label>
            Learning objective
            <select
              {...form.register("learningObjectiveId", { required: true })}
            >
              <option value="">Select an objective</option>
              {objectives.data?.items.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.subjectName} / {item.topicName} / {item.title}
                </option>
              ))}
            </select>
          </label>
          <label>
            Canonical statement
            <textarea
              rows={5}
              aria-invalid={statementValidation.quality === "INVALID"}
              aria-describedby="canonical-statement-quality"
              {...form.register("canonicalStatement", { required: true })}
            />
          </label>
          {statementValidation.quality !== "VALID" && <div id="canonical-statement-quality" className={statementValidation.quality === "INVALID" ? "error" : "warning"} role={statementValidation.quality === "INVALID" ? "alert" : "status"}>
            <strong>{statementValidation.quality === "INVALID" ? "Enter a meaningful factual statement" : "This statement may need clarification"}</strong>
            <ul>{statementValidation.issues.map((issue) => <li key={issue}>{issue}</li>)}</ul>
          </div>}
          <div className="form-grid">
            <label>
              Valid from
              <input type="date" {...form.register("validFrom")} />
            </label>
            <label>
              Valid to
              <input type="date" {...form.register("validTo")} />
            </label>
          </div>
          <fieldset>
            <legend>Sources</legend>
            <p>Select one or more active source references.</p>
            {sources.data?.items.length === 0 && (
              <p>
                No active sources are available. Create one under Sources first.
              </p>
            )}
            {sources.data?.items.map((source) => (
              <label className="checkbox" key={source.id}>
                <input
                  type="checkbox"
                  value={source.id}
                  {...form.register("sourceIds", { required: true })}
                />
                <span>
                  {source.publisher} — {source.title}
                </span>
              </label>
            ))}
          </fieldset>
          <section className="card">
            <h2>Preview</h2>
            <p>
              {statement || "The canonical statement preview appears here."}
            </p>
          </section>
          {fact.data?.reviewStatus === "APPROVED" && (
            <p className="warning card">
              Saving changes creates a new draft version. The approved
              historical version remains unchanged.
            </p>
          )}
          {save.error && (
            <p role="alert" className="error">
              {save.error.message}
            </p>
          )}
          <button disabled={save.isPending || !author}>Save draft</button>
        </form>
        {edit && fact.data && (
          <AiQuestionGenerationWorkspace
            key={`questions:${fact.data.id}:${fact.data.currentVersionId}:${fact.data.version}`}
            fact={fact.data}
          />
        )}
        {edit && fact.data && (
          <AiEditorialWorkspace
            key={`${fact.data.id}:${fact.data.currentVersionId}:${fact.data.version}`}
            fact={fact.data}
            enabled={author || reviewer}
            canMutate={author}
            sourcesReady={fact.data.sourceIds.every((sourceId) =>
              Boolean(sources.data?.items.find((source) => source.id === sourceId)?.contentChecksum),
            )}
          />
        )}
        {edit && fact.data && (
          <div className="actions">
            {author &&
              ["UNREVIEWED", "REJECTED", "REQUIRES_UPDATE"].includes(
                fact.data.reviewStatus,
              ) && (
                <button onClick={() => action.mutate("submit")}>
                  Submit for review
                </button>
              )}
            {reviewer && fact.data.reviewStatus === "UNDER_REVIEW" && (
              <>
                <button onClick={() => action.mutate("approve")}>
                  Approve
                </button>
                <button onClick={() => action.mutate("reject")}>Reject</button>
                <button onClick={() => action.mutate("update")}>
                  Require update
                </button>
              </>
            )}
            {reviewer && fact.data.status !== "RETIRED" && (
              <button
                onClick={() =>
                  confirm("Retire this fact?") && action.mutate("retire")
                }
              >
                Retire
              </button>
            )}
            {author &&
              fact.data.status === "DRAFT" &&
              fact.data.reviewStatus === "UNREVIEWED" && (
                <SafeDeleteDialog
                  entityName={fact.data.canonicalStatement}
                  entityLabel="Knowledge fact"
                  onDelete={(reason) =>
                    unwrapVoid(
                      deleteKnowledgeFact({
                        client: contentServiceClient,
                        path: { knowledgeFactId: id! },
                        query: { reason: reason || undefined },
                      }),
                    )
                  }
                  onDeleted={() => {
                    qc.invalidateQueries({
                      queryKey: adminQueryKeys.facts.all,
                    });
                    navigate("/knowledge/facts");
                  }}
                />
              )}
            {action.error && (
              <p role="alert" className="error">
                {action.error.message}
              </p>
            )}
          </div>
        )}
        {edit && (
          provenance.data && <section className="card">
            <h2>AI-assisted draft provenance</h2>
            <p><strong>Source:</strong> {provenance.data.sourceTitle}</p>
            <p><strong>Original proposal:</strong> {provenance.data.originalProposedText}</p>
            <p><strong>Accepted text:</strong> {provenance.data.finalAcceptedText}</p>
            <p><strong>Generation:</strong> {provenance.data.provider} / {provenance.data.model} · {provenance.data.promptVersion}</p>
            <p><strong>Accepted by:</strong> {provenance.data.acceptingUserId} · {new Date(provenance.data.acceptedAt).toLocaleString()}</p>
          </section>
        )}
        {edit && (
          <section className="card">
            <h2>Version history</h2>
            {versions.data?.map((version) => (
              <article key={version.id}>
                <h3>
                  Version {version.versionNumber} — {version.reviewStatus}
                </h3>
                <p>{version.canonicalStatement}</p>
                <small>
                  Author {version.authorId}
                  {version.reviewerId
                    ? ` · Reviewer ${version.reviewerId}`
                    : ""}{" "}
                  · {new Date(version.createdAt).toLocaleString()}
                </small>
                {version.reviewNote && <p>Review note: {version.reviewNote}</p>}
              </article>
            ))}
          </section>
        )}
      </AsyncState>
    </>
  );
}
