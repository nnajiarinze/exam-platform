import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const contract = resolve(process.cwd(), '../../contracts/openapi/content-service-v1.yaml');
if (!existsSync(contract)) {
  console.error('Content Service OpenAPI contract is missing: contracts/openapi/content-service-v1.yaml');
  console.error('Generation is intentionally blocked rather than inventing Content Service endpoints or DTOs.');
  process.exit(2);
}
const result = spawnSync('openapi-ts', ['-i', contract, '-o', 'src/api/generated', '-c', '@hey-api/client-fetch'], { stdio: 'inherit', shell: true });
process.exit(result.status ?? 1);
