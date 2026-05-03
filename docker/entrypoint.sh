#!/usr/bin/env bash
set -euo pipefail

: "${INFISICAL_CLIENT_ID:?INFISICAL_CLIENT_ID is required}"
: "${INFISICAL_CLIENT_SECRET:?INFISICAL_CLIENT_SECRET is required}"
: "${INFISICAL_PROJECT_ID:?INFISICAL_PROJECT_ID is required}"

INFISICAL_ENV="${INFISICAL_ENV:-staging}"

# Get Infisical Machine Identity Token
INFISICAL_TOKEN="$(infisical login \
  --method=universal-auth \
  --client-id="$INFISICAL_CLIENT_ID" \
  --client-secret="$INFISICAL_CLIENT_SECRET" \
  --silent --plain)"
export INFISICAL_TOKEN

exec infisical run \
  --projectId="$INFISICAL_PROJECT_ID" \
  --env="$INFISICAL_ENV" \
  --path=/application \
  -- java $JAVA_OPTS -jar /app/app.jar
