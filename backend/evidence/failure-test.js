import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

export const status200 = new Counter('status_200');
export const status502 = new Counter('status_502');
export const status503 = new Counter('status_503');
export const statusOther = new Counter('status_other');

export const options = {
  scenarios: {
    failure_experiment: {
      executor: 'shared-iterations',
      iterations: 800,
      vus: 10,
      maxDuration: '2m',
    },
  },
};

export default function () {
  const res = http.get('http://nginx/api/orgs/public');
  if (res.status === 200) {
    status200.add(1);
  } else if (res.status === 502) {
    status502.add(1);
  } else if (res.status === 503) {
    status503.add(1);
  } else {
    statusOther.add(1);
  }
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
