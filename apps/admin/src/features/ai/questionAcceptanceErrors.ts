import { ApiError } from "../../api/errors/ApiError";

export function questionAcceptanceError(error: unknown) {
  if (!(error instanceof ApiError)) return "The proposal could not be accepted. Try again.";
  switch (error.code) {
    case "AI_QUESTION_PROPOSAL_NOT_ACCEPTABLE":
      return "This proposal has already been accepted or rejected.";
    case "AI_QUESTION_DUPLICATE":
      return "An identical canonical Question already exists.";
    case "AI_QUESTION_PROPOSAL_STALE":
    case "AI_QUESTION_GENERATION_INPUT_STALE":
      return "The linked Knowledge Fact has changed since this proposal was generated. Generate a new proposal.";
    case "AI_QUESTION_GENERATION_SOURCE_CHANGED":
    case "AI_QUESTION_GENERATION_EVIDENCE_NOT_GROUNDED":
      return "The proposal no longer passes grounding validation.";
    case "AI_QUESTION_INTELLIGENCE_REJECTED":
      return "The proposal no longer passes Question Intelligence validation.";
    case "FORBIDDEN":
      return "You do not have permission to accept this proposal.";
    default:
      return error.message;
  }
}
