import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    fixed_1000_requests: {
      executor: 'shared-iterations',
      iterations: 1000,
      vus: 10,
      maxDuration: '3m',
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'], // 0 failures expected
  },
};

export default function () {
  const res = http.get('http://nginx/api/orgs/public');
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
