import { describe, expect, it } from "vitest";
import { validateEditorialInput } from "./editorialInputValidation";

describe("editorial input validation", () => {
  it("rejects the observed test text", () => {
    const result = validateEditorialInput("random shit to test asdfsdfas sffasdfsdfwwas safdsaf ds");
    expect(result.quality).toBe("INVALID");
    expect(result.issues.join(" ")).toMatch(/Placeholder|keyboard/);
  });
  it("accepts valid Swedish civic content", () => {
    expect(validateEditorialInput("Kommuner ansvarar för grundskolan.").quality).toBe("VALID");
  });
});
