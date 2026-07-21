import { useState } from "react";
import { ApiError } from "../api/errors/ApiError";

export function SafeDeleteDialog({
  entityName,
  entityLabel,
  requiresTypedName = false,
  onDelete,
  onDeleted,
}: {
  entityName: string;
  entityLabel: string;
  requiresTypedName?: boolean;
  onDelete: (reason: string) => Promise<void>;
  onDeleted: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [typed, setTyped] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<ApiError>();
  const [pending, setPending] = useState(false);
  const close = () => {
    if (pending) return;
    setOpen(false);
    setTyped("");
    setReason("");
    setError(undefined);
  };
  const submit = async () => {
    setPending(true);
    setError(undefined);
    try {
      await onDelete(reason.trim());
      setPending(false);
      window.alert(`${entityLabel} deleted successfully.`);
      onDeleted();
    } catch (value) {
      setError(
        value instanceof ApiError
          ? value
          : new ApiError(
              "SERVER",
              value instanceof Error ? value.message : "Deletion failed.",
            ),
      );
      setPending(false);
    }
  };
  return (
    <>
      <button
        type="button"
        className="danger-button"
        onClick={() => setOpen(true)}
      >
        Delete {entityLabel.toLowerCase()}
      </button>
      {open && (
        <div className="dialog-backdrop">
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-title"
            className="delete-dialog"
          >
            <h2 id="delete-title">Delete {entityName}?</h2>
            <p>
              This permanently deletes the eligible draft{" "}
              {entityLabel.toLowerCase()} and its temporary draft data. This
              cannot be undone.
            </p>
            <p className="warning">
              Historical, released, reviewed, or referenced content cannot be
              deleted. Archive or retire it instead.
            </p>
            {requiresTypedName && (
              <label>
                Type <strong>{entityName}</strong> to confirm
                <input
                  value={typed}
                  onChange={(event) => setTyped(event.target.value)}
                />
              </label>
            )}
            <label>
              Reason (optional)
              <textarea
                value={reason}
                onChange={(event) => setReason(event.target.value)}
              />
            </label>
            {error && (
              <div role="alert" className="deletion-blockers">
                <p className="error">{error.message}</p>
                {error.blockingReferences.map((item) => (
                  <p key={item.type}>
                    <strong>{item.type.replaceAll("_", " ")}:</strong>{" "}
                    {item.message}
                    {item.recommendedAction && (
                      <>
                        {" "}
                        Suggested action: {item.recommendedAction.toLowerCase()}
                        .
                      </>
                    )}
                  </p>
                ))}
              </div>
            )}
            <div className="actions">
              <button type="button" onClick={close}>
                Cancel
              </button>
              <button
                type="button"
                className="danger-button"
                disabled={
                  pending || (requiresTypedName && typed !== entityName)
                }
                onClick={submit}
              >
                {pending ? "Deleting…" : "Delete permanently"}
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
