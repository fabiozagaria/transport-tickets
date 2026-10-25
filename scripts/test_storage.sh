#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# A fresh disposable database keeps migration fixtures away from application data.
trap 'docker compose -f compose.yaml -f compose.test.yaml rm -sf mysql-test' EXIT
docker compose -f compose.yaml -f compose.test.yaml rm -sf mysql-test
docker compose -f compose.yaml -f compose.test.yaml run --rm checks
