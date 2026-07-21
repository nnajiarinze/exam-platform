import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AiEditorialWorkspace } from "./AiEditorialWorkspace";
import type { KnowledgeFact } from "../../../api/generated";

const fact: KnowledgeFact = {
  id: "11111111-1111-1111-1111-111111111111", learningObjectiveId: "22222222-2222-2222-2222-222222222222",
  currentVersionId: "33333333-3333-3333-3333-333333333333", canonicalStatement: "Riksdagen beslutar och regeringen verkställer.",
  reviewStatus: "UNREVIEWED", status: "DRAFT", learningObjectiveTitle: "Parliament",
  topicId: "44444444-4444-4444-4444-444444444444", topicName: "Democracy",
  subjectId: "55555555-5555-5555-5555-555555555555", subjectName: "Society", sourceCount: 1,
  sourceIds: ["66666666-6666-6666-6666-666666666666"], createdAt: "2026-07-21T08:00:00Z",
  updatedAt: "2026-07-21T08:00:00Z", version: 0,
};
const job = { id: "77777777-7777-7777-7777-777777777777", operationType: "SPLIT_FACT", learningObjectiveId: fact.learningObjectiveId,
  learningObjectiveTitle: fact.learningObjectiveTitle, requestedBy: "author", requestedCount: 5, status: "COMPLETED", provider: "FAKE",
  model: "deterministic-v1", promptVersion: "knowledge-fact-split-v1", createdAt: "2026-07-21T08:00:00Z", retryCount: 0, resultCount: 2, findingCount: 0, version: 2 };
const proposal = (id: string, text: string, order: number) => ({ id, generationJobId: job.id, operationType: "SPLIT_FACT", targetFactId: fact.id,
  originalText: fact.canonicalStatement, proposedText: text, editedText: text, rationale: "Atomic split", sourceEvidence: [{ sourceId: fact.sourceIds[0], quote: fact.canonicalStatement }],
  warnings: [], coverage: { summary: "Supported original portion" }, proposalMetadata: {}, proposalOrder: order, status: "PROPOSED", editCount: 0,
  createdAt: "2026-07-21T08:00:00Z", updatedAt: "2026-07-21T08:00:00Z", version: 0 });

function renderWorkspace({ canMutate = true, sourcesReady = true, override = {} }: { canMutate?: boolean; sourcesReady?: boolean; override?: Partial<KnowledgeFact> } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><AiEditorialWorkspace fact={{ ...fact, ...override }} enabled canMutate={canMutate} sourcesReady={sourcesReady} /></QueryClientProvider>);
}
function json(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } }); }

describe("AI editorial workspace", () => {
  it("offers all seven scoped operations to an author editing a draft", () => {
    renderWorkspace();
    expect(screen.getAllByRole("option")).toHaveLength(7);
    for (const name of ["Rewrite for clarity", "Simplify language", "Check atomicity", "Split fact", "Check source support", "Detect ambiguity", "Editorial review notes"])
      expect(screen.getByRole("option", { name })).toBeInTheDocument();
    expect(screen.getByText(/AI output is advisory/)).toBeInTheDocument();
  });

  it("restricts reviewers and immutable facts to the three analysis-only operations", () => {
    renderWorkspace({ canMutate: false, override: { reviewStatus: "UNDER_REVIEW" } });
    expect(screen.getAllByRole("option")).toHaveLength(3);
    expect(screen.queryByRole("option", { name: "Split fact" })).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Check source support" })).toBeInTheDocument();
  });

  it("blocks grounded operations without stored Source content but permits wording-only ambiguity", async () => {
    renderWorkspace({ sourcesReady: false });
    expect(screen.getByRole("alert")).toHaveTextContent(/Every linked Source needs stored content/);
    expect(screen.getByRole("button", { name: "Run editorial analysis" })).toBeDisabled();
    await userEvent.selectOptions(screen.getByLabelText("Editorial operation"), "DETECT_AMBIGUITY");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run editorial analysis" })).toBeEnabled();
  });

  it("renders ordered split proposals, selection, evidence, coverage, and safe keep-original confirmation", async () => {
    const proposals = [proposal("88888888-8888-8888-8888-888888888888", "Riksdagen beslutar.", 0), proposal("99999999-9999-9999-9999-999999999999", "Regeringen verkställer.", 1)];
    vi.stubGlobal("fetch", vi.fn().mockImplementation((request: Request) => {
      if (request.url.endsWith("/editorial-jobs") && request.method === "POST") return Promise.resolve(json(job, 202));
      if (request.url.includes("/proposals")) return Promise.resolve(json(proposals));
      if (request.url.includes("/findings")) return Promise.resolve(json([]));
      return Promise.resolve(json(job));
    }));
    renderWorkspace();
    await userEvent.selectOptions(screen.getByLabelText("Editorial operation"), "SPLIT_FACT");
    await userEvent.click(screen.getByRole("button", { name: "Run editorial analysis" }));
    expect(await screen.findByRole("heading", { name: "Proposed Fact 1 · Changed" })).toBeInTheDocument();
    expect(screen.getAllByText("Supported original portion")).toHaveLength(2);
    await userEvent.click(screen.getByLabelText("Select Proposed Fact 1"));
    await userEvent.click(screen.getByLabelText("Select Proposed Fact 2"));
    await userEvent.click(screen.getByRole("button", { name: "Review 2 selected drafts" }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("Keep original and create selected drafts");
    expect(dialog).toHaveTextContent("DRAFT and UNREVIEWED");
    expect(dialog).toHaveTextContent("normal human review");
  });

  it("renders advisory findings and accessible dismissal without raw provider JSON", async () => {
    const finding = { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", generationJobId: job.id, operationType: "CHECK_SOURCE_SUPPORT", targetFactId: fact.id,
      targetFactVersionId: fact.currentVersionId, findingType: "CLAIM_PARTIALLY_SUPPORTED", severity: "WARNING", title: "Claim is only partially supported",
      message: "Advisory comparison.", affectedText: "regeringen", sourceEvidence: [{ sourceId: fact.sourceIds[0], quote: fact.canonicalStatement }],
      suggestedAction: "HUMAN_REVIEW", metadata: { supportStatus: "PARTIALLY_SUPPORTED", unsupportedFragments: ["regeringen"] }, status: "OPEN",
      createdAt: "2026-07-21T08:00:00Z", version: 0 };
    vi.stubGlobal("fetch", vi.fn().mockImplementation((request: Request) => {
      if (request.url.endsWith("/editorial-jobs") && request.method === "POST") return Promise.resolve(json({ ...job, operationType: "CHECK_SOURCE_SUPPORT", resultCount: 0, findingCount: 1 }, 202));
      if (request.url.includes("/proposals")) return Promise.resolve(json([]));
      if (request.url.includes("/findings")) return Promise.resolve(json([finding]));
      return Promise.resolve(json({ ...job, operationType: "CHECK_SOURCE_SUPPORT", resultCount: 0, findingCount: 1 }));
    }));
    renderWorkspace();
    await userEvent.selectOptions(screen.getByLabelText("Editorial operation"), "CHECK_SOURCE_SUPPORT");
    await userEvent.click(screen.getByRole("button", { name: "Run editorial analysis" }));
    expect(await screen.findByRole("heading", { name: "Claim is only partially supported" })).toBeInTheDocument();
    expect(screen.getByText(/PARTIALLY SUPPORTED/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Dismiss finding" })).toBeInTheDocument();
    expect(screen.getByText(/not a validation or reviewer decision/)).toBeInTheDocument();
  });
});
