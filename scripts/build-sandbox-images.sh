#!/usr/bin/env bash
# Builds the four controlled runner images used by the judge. Needs Docker and (the first time) internet access
# to pull the pinned base images. Run once, and again after changing sandbox/Dockerfiles.
set -euo pipefail
cd "$(dirname "$0")/.."
build() { echo "==> $1"; docker build -q -t "$1" -f "sandbox/Dockerfiles/$2" sandbox/Dockerfiles; }
build codesentinel/runner-native:1 native.Dockerfile
build codesentinel/runner-java:1   java.Dockerfile
build codesentinel/runner-python:1 python.Dockerfile
build codesentinel/runner-node:1   node.Dockerfile
echo "Sandbox images ready:"; docker images --format '  {{.Repository}}:{{.Tag}}  {{.Size}}' | grep '^  codesentinel/runner' || true
