#!/usr/bin/env bash
#
# Runs tests (plus the per-service JaCoCo report) for every microservice,
# then merges all coverage into one aggregated report.
#
set -euo pipefail
cd "$(dirname "$0")"

services=(auth-service user-service product-service order-service log-service)

for s in "${services[@]}"; do
    echo ">>> tests + per-service report: $s"
    (cd "$s" && ./gradlew cleanTest test jacocoTestReport)
done

echo ">>> generating aggregated report"
./gradlew jacocoAggregateReport

echo
echo "Aggregated HTML report: $(pwd)/build/reports/jacoco/aggregate/html/index.html"
