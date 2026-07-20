import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../app/auth/AuthContext";
import { AppRouter } from "../app/router/AppRouter";
import { AdminRole } from "../app/permissions/permissions";
import { adminSessionStorageKey } from "../app/auth/authSession";
function json(body: object) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}
function renderRoute(path: string) {
  sessionStorage.setItem(
    adminSessionStorageKey,
    JSON.stringify({
      id: "reviewer",
      displayName: "Reviewer",
      roles: [AdminRole.ContentReviewer],
    }),
  );
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <AppRouter />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
const item = {
  id: "11111111-1111-1111-1111-111111111111",
  contentType: "KNOWLEDGE_FACT",
  contentId: "22222222-2222-2222-2222-222222222222",
  contentVersionId: "33333333-3333-3333-3333-333333333333",
  contentCode: "FACT",
  titleOrSummary: "Sweden has municipalities.",
  reviewStatus: "UNDER_REVIEW",
  lifecycleStatus: "DRAFT",
  authorId: "author",
  authorName: "author",
  submittedAt: "2026-07-20T10:00:00Z",
  updatedAt: "2026-07-20T10:00:00Z",
  assignedReviewerId: null,
  assignedReviewerName: null,
  priority: "NORMAL",
  learningObjectiveId: "44444444-4444-4444-4444-444444444444",
  learningObjectiveTitle: "Municipalities",
  topicId: "55555555-5555-5555-5555-555555555555",
  topicName: "Government",
  subjectId: "66666666-6666-6666-6666-666666666666",
  subjectName: "Society",
  version: 0,
  stale: false,
  impactWarningCount: 1,
};
describe("Phase 5 review workspace", () => {
  it("renders queue summary, filters, assignment and pagination", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockImplementation((request: Request) =>
          Promise.resolve(
            json(
              request.url.includes("/summary")
                ? {
                    awaitingReview: 1,
                    assignedToMe: 0,
                    unassigned: 1,
                    requiresUpdate: 0,
                    highPriority: 0,
                    oldestPendingAgeDays: 1,
                  }
                : {
                    items: [item],
                    page: 0,
                    size: 20,
                    totalItems: 1,
                    totalPages: 1,
                  },
            ),
          ),
        ),
    );
    renderRoute("/reviews");
    expect(
      screen.getByRole("heading", { name: "Reviews" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("Sweden has municipalities."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Assigned to me")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Claim" })).toBeInTheDocument();
  });
  it("renders fact context, comments, decisions, warnings and history", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          json({
            ...item,
            content: {
              version: 1,
              canonicalStatement: item.titleOrSummary,
              validFrom: null,
              validTo: null,
            },
            sourceContext: [
              {
                publisher: "Authority",
                title: "Source",
                url: "https://example.se",
                reviewStatus: "REVIEWED",
                status: "ACTIVE",
              },
            ],
            history: [
              {
                id: "77777777-7777-7777-7777-777777777777",
                contentVersionId: item.contentVersionId,
                action: "SUBMITTED",
                actorId: "author",
                createdAt: item.submittedAt,
              },
            ],
            comments: [],
          }),
        ),
    );
    renderRoute(`/reviews/${item.id}`);
    expect(
      await screen.findByRole("heading", { name: "Knowledge fact review" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Impact warning/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Review history" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("New comment")).toBeInTheDocument();
  });
});
