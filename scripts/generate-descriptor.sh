#!/usr/bin/env bash
# Compiles test-server/src/main/proto/greeter.proto into a FileDescriptorSet that the
# dynamic-client can consume. This is the step you'd run (or automate) for every
# external backend whose contract you want to onboard, in place of generating Java stubs.
#
# Requires `protoc` on PATH (matching version isn't critical for descriptor sets,
# but keep it reasonably current). On macOS: `brew install protobuf`.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
PROTO_DIR="$ROOT_DIR/test-server/src/main/proto"
OUT_FILE="$ROOT_DIR/greeter.desc"

protoc \
  --proto_path="$PROTO_DIR" \
  --descriptor_set_out="$OUT_FILE" \
  --include_imports \
  "$PROTO_DIR/greeter.proto"

echo "Wrote $OUT_FILE"
echo "Register it with the dynamic client via:"
echo "  curl -F file=@$OUT_FILE http://localhost:8080/descriptors/greeter-test-service"
