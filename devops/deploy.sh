#!/usr/bin/env bash
# Simulated deployment: checks the artifact that the pipeline built and reports where it would
# go. usage: deploy.sh <dev|cert> <jar>
# The credential arrives in DEPLOY_TOKEN from the pipeline's secret store and is never printed.
set -euo pipefail

environment="${1:?usage: deploy.sh <dev|cert> <jar>}"
jar="${2:?usage: deploy.sh <dev|cert> <jar>}"

case "$environment" in
  dev|cert) ;;
  *) echo "Unknown environment: $environment" >&2; exit 2 ;;
esac

if [ ! -f "$jar" ]; then
  echo "Artifact not found: $jar" >&2
  exit 1
fi

if [ -z "${DEPLOY_TOKEN:-}" ]; then
  echo "DEPLOY_TOKEN is not set for $environment: a real deployment would stop here."
else
  echo "Credential for $environment received."
fi

echo "Artifact: $(basename "$jar") ($(wc -c < "$jar") bytes)"
echo "sha256:   $(sha256sum "$jar" | cut -d' ' -f1)"
echo "Deployed config-server to $environment (simulated)."
