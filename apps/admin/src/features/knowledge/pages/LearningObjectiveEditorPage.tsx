import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import {
  archiveLearningObjective,
  createLearningObjective,
  deleteLearningObjective,
  getLearningObjective,
  updateLearningObjective,
  type LearningObjectiveRequest,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap, unwrapVoid } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";
import { AsyncState } from "../../../components/AsyncState";
import { useUnsavedWarning } from "../../../hooks/useUnsavedWarning";
import { SafeDeleteDialog } from "../../../components/SafeDeleteDialog";
import { useAuth } from "../../../app/auth/AuthContext";
import { AdminRole } from "../../../app/permissions/permissions";

export function LearningObjectiveEditorPage() {
  const { id } = useParams();
  const edit = Boolean(id);
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { admin } = useAuth();
  const query = useQuery({
    queryKey: adminQueryKeys.objectives.detail(id ?? "new"),
    queryFn: () =>
      unwrap(
        getLearningObjective({
          client: contentServiceClient,
          path: { learningObjectiveId: id! },
        }),
      ),
    enabled: edit,
  });
  const form = useForm<LearningObjectiveRequest>({
    defaultValues: {
      topicId: "",
      code: "",
      title: "",
      description: "",
      status: "DRAFT",
    },
  });
  useEffect(() => {
    if (query.data) form.reset(query.data);
  }, [query.data, form]);
  useUnsavedWarning(form.formState.isDirty);
  const save = useMutation({
    mutationFn: (body: LearningObjectiveRequest) =>
      edit
        ? unwrap(
            updateLearningObjective({
              client: contentServiceClient,
              path: { learningObjectiveId: id! },
              body: { ...body, version: query.data!.version },
            }),
          )
        : unwrap(
            createLearningObjective({ client: contentServiceClient, body }),
          ),
    onSuccess: (item) => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.objectives.all });
      navigate(`/knowledge/objectives/${item.id}`);
    },
  });
  const archive = () =>
    query.data &&
    confirm("Archive this learning objective?") &&
    unwrap(
      archiveLearningObjective({
        client: contentServiceClient,
        path: { learningObjectiveId: id! },
        body: { version: query.data.version },
      }),
    ).then(() =>
      qc.invalidateQueries({ queryKey: adminQueryKeys.objectives.detail(id!) }),
    );
  return (
    <>
      <header className="page-header">
        <h1>{edit ? "Learning objective" : "Create learning objective"}</h1>
      </header>
      <AsyncState loading={edit && query.isPending} error={query.error}>
        <form
          className="form"
          onSubmit={form.handleSubmit((value) => save.mutate(value))}
        >
          <label>
            Topic ID
            <input {...form.register("topicId", { required: true })} />
            <small>Copy the topic UUID from the Exam Structure screen.</small>
          </label>
          <label>
            Code
            <input {...form.register("code", { required: true })} />
          </label>
          <label>
            Title
            <input {...form.register("title", { required: true })} />
          </label>
          <label>
            Description
            <textarea {...form.register("description")} />
          </label>
          <label>
            Status
            <select {...form.register("status")}>
              <option>DRAFT</option>
              <option>ACTIVE</option>
              <option>ARCHIVED</option>
            </select>
          </label>
          {save.error && (
            <p role="alert" className="error">
              {save.error.message}
            </p>
          )}
          <div className="actions">
            <button disabled={save.isPending}>Save objective</button>
            {edit && query.data?.status !== "ARCHIVED" && (
              <button type="button" onClick={archive}>
                Archive
              </button>
            )}
            {edit &&
              query.data?.status === "DRAFT" &&
              admin?.roles.includes(AdminRole.Admin) && (
                <SafeDeleteDialog
                  entityName={query.data.title}
                  entityLabel="Learning objective"
                  onDelete={(reason) =>
                    unwrapVoid(
                      deleteLearningObjective({
                        client: contentServiceClient,
                        path: { learningObjectiveId: id! },
                        query: { reason: reason || undefined },
                      }),
                    )
                  }
                  onDeleted={() => {
                    qc.invalidateQueries({
                      queryKey: adminQueryKeys.objectives.all,
                    });
                    navigate("/knowledge/objectives");
                  }}
                />
              )}
          </div>
        </form>
      </AsyncState>
    </>
  );
}
