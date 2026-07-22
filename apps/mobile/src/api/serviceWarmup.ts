import { environmentConfig } from '../config/environment';

const WARMUP_COOLDOWN_MS = 60_000;
let lastWarmupAt = 0;
let warmupInFlight: Promise<void> | undefined;

export function wakeHostedServices(): Promise<void> {
  if (environmentConfig.warmupUrls.length === 0) return Promise.resolve();
  if (warmupInFlight) return warmupInFlight;
  if (Date.now() - lastWarmupAt < WARMUP_COOLDOWN_MS) return Promise.resolve();

  lastWarmupAt = Date.now();
  warmupInFlight = Promise.allSettled(
    environmentConfig.warmupUrls.map(url => fetch(url, { method: 'GET' })),
  ).then(() => undefined).finally(() => { warmupInFlight = undefined; });
  return warmupInFlight;
}
