import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams, Link } from "react-router-dom";
import {
  createExam,
  deleteExam,
  getExam,
  updateExam,
  archiveExam,
  listExamVersions,
  createExamVersion,
  type ExamRequest,
  type ExamVersionRequest,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap, unwrapVoid } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";
import { AsyncState } from "../../../components/AsyncState";
import { useUnsavedWarning } from "../../../hooks/useUnsavedWarning";
import { SafeDeleteDialog } from "../../../components/SafeDeleteDialog";
import { useAuth } from "../../../app/auth/AuthContext";
import { AdminRole } from "../../../app/permissions/permissions";
export function ExamEditorPage() {
  const { id } = useParams();
  const edit = !!id;
  const nav = useNavigate();
  const qc = useQueryClient();
  const { admin } = useAuth();
  const q = useQuery({
    queryKey: adminQueryKeys.exams.detail(id ?? "new"),
    queryFn: () =>
      unwrap(getExam({ client: contentServiceClient, path: { examId: id! } })),
    enabled: edit,
  });
  const versions = useQuery({
    queryKey: adminQueryKeys.exams.versions(id ?? ""),
    queryFn: () =>
      unwrap(
        listExamVersions({
          client: contentServiceClient,
          path: { examId: id! },
        }),
      ),
    enabled: edit,
  });
  const f = useForm<ExamRequest>({
    defaultValues: {
      code: "SWEDISH_CITIZENSHIP",
      name: "Swedish Citizenship",
      countryCode: "SE",
      status: "DRAFT",
    },
  });
  useEffect(() => {
    if (q.data) f.reset(q.data);
  }, [q.data, f]);
  useUnsavedWarning(f.formState.isDirty);
  const save = useMutation({
    mutationFn: (v: ExamRequest) =>
      edit
        ? unwrap(
            updateExam({
              client: contentServiceClient,
              path: { examId: id! },
              body: { ...v, version: q.data!.version },
            }),
          )
        : unwrap(createExam({ client: contentServiceClient, body: v })),
    onSuccess: (x) => {
      qc.invalidateQueries({ queryKey: adminQueryKeys.exams.all });
      nav(`/exam-structure/exams/${x.id}`);
    },
  });
  const addVersion = useMutation({
    mutationFn: () => {
      const code = window.prompt("Version code");
      if (!code) throw new Error("Version code is required");
      const body: ExamVersionRequest = {
        versionCode: code,
        displayName: window.prompt("Display name") || code,
        status: "DRAFT",
      };
      return unwrap(
        createExamVersion({
          client: contentServiceClient,
          path: { examId: id! },
          body,
        }),
      );
    },
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: adminQueryKeys.exams.versions(id!) }),
  });
  return (
    <>
      <header className="page-header">
        <h1>{edit ? "Exam details" : "Create exam"}</h1>
      </header>
      <AsyncState loading={edit && q.isPending} error={q.error}>
        <form className="form" onSubmit={f.handleSubmit((v) => save.mutate(v))}>
          <label>
            Code
            <input {...f.register("code", { required: true })} />
          </label>
          <label>
            Name
            <input {...f.register("name", { required: true })} />
          </label>
          <label>
            Country code
            <input
              maxLength={2}
              {...f.register("countryCode", { required: true })}
            />
          </label>
          <label>
            Status
            <select {...f.register("status")}>
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
          <button disabled={save.isPending}>Save exam</button>
          {edit && q.data?.status !== "ARCHIVED" && (
            <button
              type="button"
              onClick={() =>
                confirm("Archive this exam?") &&
                unwrap(
                  archiveExam({
                    client: contentServiceClient,
                    path: { examId: id! },
                    body: { version: q.data!.version },
                  }),
                ).then(() => location.reload())
              }
            >
              Archive
            </button>
          )}
          {edit &&
            q.data?.status === "DRAFT" &&
            versions.data?.length === 0 &&
            admin?.roles.includes(AdminRole.Admin) && (
              <SafeDeleteDialog
                entityName={q.data.name}
                entityLabel="Exam"
                requiresTypedName
                onDelete={(reason) =>
                  unwrapVoid(
                    deleteExam({
                      client: contentServiceClient,
                      path: { examId: id! },
                      query: { reason: reason || undefined },
                    }),
                  )
                }
                onDeleted={() => {
                  qc.invalidateQueries({ queryKey: adminQueryKeys.exams.all });
                  nav("/exam-structure");
                }}
              />
            )}
        </form>
        {edit && (
          <section className="card">
            <h2>Exam versions</h2>
            <button onClick={() => addVersion.mutate()}>Create version</button>
            {versions.data?.map((v) => (
              <p key={v.id}>
                <Link to={`/exam-structure/exam-versions/${v.id}`}>
                  {v.displayName}
                </Link>{" "}
                — {v.status}
              </p>
            ))}
          </section>
        )}
      </AsyncState>
    </>
  );
}
