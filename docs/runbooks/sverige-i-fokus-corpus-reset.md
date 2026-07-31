# Sverige i fokus corpus reset

This runbook is limited to the `content`, `learning`, and `ai` databases. `corpusctl.py` refuses any database name that differs from its domain, refuses known US-East/Render host markers, and requires a pinned hosted-host fingerprint. Identity is never a valid target.

Use PostgreSQL 18, `age`, and a dedicated age key whose private identity file is stored outside the repository with mode `0600`.

```bash
export CORPUS_CONTENT_DATABASE_URL='postgresql://…/content'
export CORPUS_LEARNING_DATABASE_URL='postgresql://…/learning'
export CORPUS_AI_DATABASE_URL='postgresql://…/ai'
export CORPUS_AGE_RECIPIENT='age1…'
export CORPUS_AGE_IDENTITY_FILE='/secure/external/path/age-identity.txt'

python3 scripts/corpusctl.py inspect --environment local
python3 scripts/corpusctl.py backup --environment local --backup-dir /secure/external/path/backup-set
python3 scripts/corpusctl.py reset --environment local \
  --verified-backup /secure/external/path/backup-set/local-verified-backup.json \
  --require-verified-backup --dry-run
python3 scripts/corpusctl.py reset --environment local \
  --verified-backup /secure/external/path/backup-set/local-verified-backup.json \
  --require-verified-backup
python3 scripts/corpusctl.py import --environment local
python3 scripts/corpusctl.py coverage --environment local
```

For hosted execution, set `CORPUS_EXPECTED_HOSTED_HOST_SHA256` to the separately verified current EU Frankfurt direct-host fingerprint and use `--environment hosted`. Never derive or reuse that value from a retired US-East configuration.

Each backup manifest records exact pre-reset table counts, archive paths, encrypted SHA-256 checksums, sizes, permission modes, target fingerprints, and PostgreSQL 18 archive validation.

Restore one domain to an empty recovery database first:

```bash
age -d -i /secure/external/path/age-identity.txt hosted-content-….dump.age \
  | /opt/homebrew/opt/postgresql@18/bin/pg_restore \
      --exit-on-error --no-owner --no-acl \
      --dbname 'postgresql://…/content_recovery'
```

Validate recovery before considering an in-place restore. Previous backup sets are never overwritten or removed.

Reset retention:

- Content keeps schema, Flyway history, and immutable `audit_event` records.
- Learning keeps schema, Flyway history, learner profiles, and learner settings; curriculum-bound progress and attempts are cleared.
- AI keeps schema, Flyway history, immutable audit, quota profiles, price profiles, and provider-circuit configuration; content jobs, proposals, findings, evidence, lineage, token reservations, and results are cleared.

The guarded Hetzner workflow uses PostgreSQL 18 in an isolated container when the host client is older. If the general offsite backup configuration is unavailable, it writes validated custom archives under `/opt/citizenship-platform/backups`, encrypts them with AES-256-CBC/PBKDF2, and protects both the backup set and separately reusable restore key with mode `0600`. Restore to a recovery database with:

```bash
openssl enc -d -aes-256-cbc -pbkdf2 \
  -pass file:/opt/citizenship-platform/backups/sverige-i-fokus-backup.key \
  -in content.dump.aes256 |
  pg_restore --exit-on-error --no-owner --no-acl --dbname 'postgresql://…/content_recovery'
```
