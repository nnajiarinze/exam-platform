\set ON_ERROR_STOP on

-- Pass generated passwords with psql -v. Values are never committed and psql
-- substitutes them only for this provisioning session.
SELECT format('CREATE ROLE content_service LOGIN PASSWORD %L', :'content_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'content_service') \gexec
SELECT format('ALTER ROLE content_service PASSWORD %L', :'content_password') \gexec

SELECT format('CREATE ROLE learning_service LOGIN PASSWORD %L', :'learning_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'learning_service') \gexec
SELECT format('ALTER ROLE learning_service PASSWORD %L', :'learning_password') \gexec

SELECT format('CREATE ROLE ai_service LOGIN PASSWORD %L', :'ai_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_service') \gexec
SELECT format('ALTER ROLE ai_service PASSWORD %L', :'ai_password') \gexec

SELECT format('CREATE ROLE identity_service LOGIN PASSWORD %L', :'identity_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'identity_service') \gexec
SELECT format('ALTER ROLE identity_service PASSWORD %L', :'identity_password') \gexec

SELECT format('GRANT content_service, learning_service, ai_service, identity_service TO %I', current_user) \gexec

SELECT 'CREATE DATABASE content OWNER content_service'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'content') \gexec
SELECT 'CREATE DATABASE learning OWNER learning_service'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'learning') \gexec
SELECT 'CREATE DATABASE ai OWNER ai_service'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'ai') \gexec
SELECT 'CREATE DATABASE identity OWNER identity_service'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'identity') \gexec

REVOKE CONNECT ON DATABASE content FROM PUBLIC;
REVOKE CONNECT ON DATABASE learning FROM PUBLIC;
REVOKE CONNECT ON DATABASE ai FROM PUBLIC;
REVOKE CONNECT ON DATABASE identity FROM PUBLIC;
GRANT CONNECT ON DATABASE content TO content_service;
GRANT CONNECT ON DATABASE learning TO learning_service;
GRANT CONNECT ON DATABASE ai TO ai_service;
GRANT CONNECT ON DATABASE identity TO identity_service;

SELECT format('REVOKE content_service, learning_service, ai_service, identity_service FROM %I', current_user) \gexec
