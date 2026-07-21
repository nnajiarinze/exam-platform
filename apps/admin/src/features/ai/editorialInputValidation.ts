export type EditorialInputQuality = "VALID" | "SUSPICIOUS" | "INVALID";
export type EditorialInputValidation = { quality: EditorialInputQuality; issues: string[] };

export function validateEditorialInput(raw: string): EditorialInputValidation {
  const text = raw.normalize("NFC").trim().replace(/\s+/g, " ");
  if (!text) return invalid("Enter a meaningful factual statement.");
  const lower = text.toLocaleLowerCase("sv");
  const issues: string[] = [];
  if (/<\s*script\b|javascript\s*:|<\s*\/?\s*[a-z][^>]*>/i.test(text)) issues.push("HTML or script content is not allowed.");
  if (/\b(ignore (all |the )?(previous|prior) instructions?|reveal (the )?(system )?prompt|approve this|publish this)\b/i.test(text)) issues.push("Instructions directed at the AI are not factual content.");
  if (/^https?:\/\/\S+$/i.test(text) || /^[0-9a-f]{8}-[0-9a-f-]{27,}$/i.test(text)) issues.push("A URL or identifier by itself is not a Knowledge Fact.");
  const mash = lower.split(/\s+/).filter((token) => /asdf|qwerty|hjkl|zxcv/i.test(token) || (token.length >= 8 && vowelRatio(token) < 0.18));
  if (/\b(lorem ipsum|todo|fixme|placeholder|test fact|random (text|shit)|abc123|xxx)\b/i.test(lower)) issues.push("Placeholder or test text was detected.");
  if (mash.length >= 2 || /([a-zåäö])\1{4,}/i.test(lower)) issues.push("Nonsensical keyboard input was detected.");
  if (text.endsWith("?")) issues.push("Write a declarative statement rather than a question.");
  if (issues.length) return { quality: "INVALID", issues };
  if (!/\b(är|har|gör|får|ansvarar|beslutar|stiftar|består|innebär|gäller|finns|ska|kan|måste|ger|skyddar|betalar|väljer|utser|granskar|styr|provides?|decides?|is|are|has|have|does?|receives?|protects?|elects?|governs?|includes?|means?)\b/iu.test(lower))
    return { quality: "SUSPICIOUS", issues: ["The statement may not contain a clear factual predicate."] };
  return { quality: "VALID", issues: [] };
}

function vowelRatio(value: string) {
  const letters = value.replace(/[^a-zåäö]/giu, "");
  return letters ? (letters.match(/[aeiouyåäö]/giu)?.length ?? 0) / letters.length : 0;
}
function invalid(message: string): EditorialInputValidation { return { quality: "INVALID", issues: [message] }; }
