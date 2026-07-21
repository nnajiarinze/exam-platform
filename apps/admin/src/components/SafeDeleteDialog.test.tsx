import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi } from "vitest";
import { ApiError } from "../api/errors/ApiError";
import { SafeDeleteDialog } from "./SafeDeleteDialog";

describe("SafeDeleteDialog", () => {
  it("requires the exact entity name for high-impact deletion", async () => {
    const user = userEvent.setup();
    const onDelete = vi.fn().mockResolvedValue(undefined);

    render(
      <SafeDeleteDialog
        entityName="Swedish citizenship"
        entityLabel="Exam"
        requiresTypedName
        onDelete={onDelete}
        onDeleted={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Delete exam" }));
    const confirm = screen.getByRole("button", { name: "Delete permanently" });
    expect(confirm).toBeDisabled();
    expect(screen.getByRole("dialog")).toHaveTextContent("cannot be undone");

    await user.type(
      screen.getByRole("textbox", { name: /Type/ }),
      "Wrong name",
    );
    expect(confirm).toBeDisabled();
    await user.clear(screen.getByRole("textbox", { name: /Type/ }));
    await user.type(
      screen.getByRole("textbox", { name: /Type/ }),
      "Swedish citizenship",
    );
    expect(confirm).toBeEnabled();
  });

  it("shows backend blocking references and the recommended alternative", async () => {
    const user = userEvent.setup();
    const conflict = new ApiError(
      "CONFLICT",
      "Question cannot be deleted",
      409,
      "CONTENT_DELETE_BLOCKED",
      [],
      undefined,
      [
        {
          type: "RELEASE_REFERENCE",
          count: 1,
          message: "Referenced by a release",
          recommendedAction: "ARCHIVE",
        },
      ],
    );

    render(
      <SafeDeleteDialog
        entityName="Q-001"
        entityLabel="Question"
        onDelete={vi.fn().mockRejectedValue(conflict)}
        onDeleted={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Delete question" }));
    await user.click(
      screen.getByRole("button", { name: "Delete permanently" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Referenced by a release",
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Suggested action: archive",
    );
  });

  it("passes the audit reason and refreshes the owning view after deletion", async () => {
    const user = userEvent.setup();
    const onDelete = vi.fn().mockResolvedValue(undefined);
    const onDeleted = vi.fn();
    vi.stubGlobal("alert", vi.fn());

    render(
      <SafeDeleteDialog
        entityName="Draft source"
        entityLabel="Source"
        onDelete={onDelete}
        onDeleted={onDeleted}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Delete source" }));
    await user.type(
      screen.getByRole("textbox", { name: /Reason/ }),
      "Duplicate",
    );
    await user.click(
      screen.getByRole("button", { name: "Delete permanently" }),
    );

    expect(onDelete).toHaveBeenCalledWith("Duplicate");
    expect(onDeleted).toHaveBeenCalledOnce();
  });
});
