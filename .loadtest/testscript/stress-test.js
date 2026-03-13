import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.K6_BASE_URL || 'http://app:8080/api/tasks';
const VUS = parseInt(__ENV.K6_VUS || '20');
const DURATION = __ENV.K6_DURATION || '2m';
const RAMP_UP = __ENV.K6_RAMP_UP || '30s';
const RAMP_DOWN = __ENV.K6_RAMP_DOWN || '30s';

export const options = {
  stages: [
    { duration: RAMP_UP, target: VUS },
    { duration: DURATION, target: VUS },
    { duration: RAMP_DOWN, target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

const CONTENT_TYPE = 'application/json';

export default function () {
  // 1. Create Task (POST)
  const idempotencyKey = uuidv4();
  const createPayload = JSON.stringify({
    imageUrl: `https://picsum.photos/seed/${idempotencyKey}/1024/768`,
  });

  const createRes = http.post(BASE_URL, createPayload, {
    headers: {
      'Content-Type': CONTENT_TYPE,
      'X-Idempotency-Key': idempotencyKey,
    },
  });

  check(createRes, {
    'Create status is 202': (r) => r.status === 202,
    'Create response has id': (r) => r.json('id') != null,
  });

  const taskId = createRes.json('id');

  if (!taskId) {
    return;
  }

  sleep(1);

  // 2. Get Task (GET) - 작업이 완료될 때까지 폴링
  let taskStatus = '';
  let getRes;
  const maxAttempts = 10;
  let attempt = 0;

  while (taskStatus !== 'COMPLETED' && taskStatus !== 'FAILED' && attempt < maxAttempts) {
    getRes = http.get(`${BASE_URL}/${taskId}`);

    check(getRes, {
      'Get Task status is 200': (r) => r.status === 200,
    });

    if (getRes.status === 200 && getRes.json('status')) {
      taskStatus = getRes.json('status');
    }

    if (taskStatus !== 'COMPLETED' && taskStatus !== 'FAILED') {
      sleep(2);
    }
    attempt++;
  }

  check(getRes, {
    'Task processing completed': (r) => r.json('status') === 'COMPLETED',
    'Task has result': (r) => r.json('status') !== 'COMPLETED' || (r.json('result') != null && r.json('result') !== ''),
  });

  sleep(1);

  // 3. List Tasks (GET)
  const listRes = http.get(`${BASE_URL}?size=5`);

  check(listRes, {
    'List Tasks status is 200': (r) => r.status === 200,
    'List Tasks returns a page': (r) => r.json('content') && Array.isArray(r.json('content')),
  });

  sleep(1);
}
