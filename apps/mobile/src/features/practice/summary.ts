export function sessionAccuracy(correct: number, total: number) { return total === 0 ? 0 : Math.round((correct / total) * 100); }
