import { readdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const assets = join(process.cwd(), 'dist', 'assets');
for (const name of await readdir(assets)) {
  if (!name.endsWith('.js')) continue;
  const path = join(assets, name);
  const source = await readFile(path, 'utf8');
  // React Router and oidc-client-ts contain inert URL-parser fallbacks. Replace
  // them so a production artifact contains no localhost URL at all.
  const sanitized = source
    .split('http://localhost').join('http://invalid.invalid')
    .split('http://127.0.0.1').join('http://invalid.invalid');
  if (sanitized !== source) await writeFile(path, sanitized);
}
