#!/usr/bin/env bash
set -euo pipefail

PROFILE="${AWS_PROFILE:-default}"
STACK_NAME="core-fhir-ci"

mapfile -t STACK_TAGS < <(python3 scripts/aws-governance-tags.py "$STACK_NAME")

aws cloudformation deploy \
  --stack-name "$STACK_NAME" \
  --template-file infra/core-fhir-ci.yml \
  --capabilities CAPABILITY_NAMED_IAM \
  --tags "${STACK_TAGS[@]}" \
  --no-fail-on-empty-changeset \
  --profile "$PROFILE"

aws cloudformation update-termination-protection \
  --stack-name "$STACK_NAME" \
  --enable-termination-protection \
  --profile "$PROFILE"

aws cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --query 'Stacks[0].Outputs' \
  --output table \
  --profile "$PROFILE"
