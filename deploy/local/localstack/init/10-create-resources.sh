#!/usr/bin/env sh
set -eu

awslocal s3api head-bucket \
  --bucket "${STEWARDMESH_INTAKE_BUCKET}" 2>/dev/null || \
  awslocal s3api create-bucket \
    --bucket "${STEWARDMESH_INTAKE_BUCKET}" \
    --create-bucket-configuration "LocationConstraint=${AWS_DEFAULT_REGION}"

awslocal sqs get-queue-url \
  --queue-name "${STEWARDMESH_MASTER_EVENTS_QUEUE}" >/dev/null 2>&1 || \
  awslocal sqs create-queue \
    --queue-name "${STEWARDMESH_MASTER_EVENTS_QUEUE}" >/dev/null

awslocal sqs get-queue-url \
  --queue-name "${STEWARDMESH_SOURCE_EVENTS_QUEUE}" >/dev/null 2>&1 || \
  awslocal sqs create-queue \
    --queue-name "${STEWARDMESH_SOURCE_EVENTS_QUEUE}" >/dev/null
