#!/bin/bash
# Runs inside LocalStack once it is ready. Same table the app auto-creates (UrlTableInitializer),
# so the table exists even before the backend starts, e.g. for the Postman / newman run.
set -euo pipefail

TABLE=bitly-urls
if awslocal dynamodb describe-table --table-name "$TABLE" >/dev/null 2>&1; then
  echo "$TABLE already exists"
  exit 0
fi

awslocal dynamodb create-table \
  --table-name "$TABLE" \
  --attribute-definitions AttributeName=short_code,AttributeType=S \
  --key-schema AttributeName=short_code,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST >/dev/null
awslocal dynamodb wait table-exists --table-name "$TABLE"
# DynamoDB deletes expired rows on its own (lazily); the app still returns 410 until then.
awslocal dynamodb update-time-to-live --table-name "$TABLE" \
  --time-to-live-specification "Enabled=true,AttributeName=expires_at" >/dev/null
echo "created $TABLE (TTL on expires_at)"
