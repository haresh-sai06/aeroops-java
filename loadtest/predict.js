// k6 load test for POST /api/v1/predict/delay
//   SCENARIO=hot  -> requests drawn from a pool of 500 realistic flights (cache warms up -> mostly Redis hits)
//   SCENARIO=cold -> every request gets a unique distance jitter -> always a cache miss -> full XGBoost inference
// Usage: k6 run -e SCENARIO=cold -e VUS=32 -e DURATION=60s --summary-export loadtest/results/cold.json loadtest/predict.js
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const reqs = new SharedArray('reqs', () => JSON.parse(open('./requests.json')));
const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'hot';

export const options = {
  vus: Number(__ENV.VUS || 32),
  duration: __ENV.DURATION || '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: { http_req_failed: ['rate<0.001'] },
};

export default function () {
  const r = Object.assign({}, reqs[Math.floor(Math.random() * reqs.length)]);
  if (SCENARIO === 'cold') r.distance = r.distance + Math.random();
  const res = http.post(`${BASE}/api/v1/predict/delay`, JSON.stringify(r), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'status 200': (x) => x.status === 200 });
}
