#!/usr/bin/env sh
set -e

exec redis-server \
  --requirepass "${REDIS_PASSWORD}" \
  --user "${REDIS_USERNAME}" on ">${REDIS_PASSWORD}" "~*" "+@all"
