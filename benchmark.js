import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '5s', target: 20 }, // Ramp up to 20 users
    { duration: '15s', target: 20 }, // Stay at 20 users
    { duration: '5s', target: 0 },  // Ramp down
  ],
};

// Assuming there's a transaction ID to query, ideally we'd pass it as an env var.
// We can test hitting an account instead, since account endpoints are also cached.
export default function () {
  const accountId = __ENV.ACCOUNT_ID || 'ACC-12345';
  const url = `http://localhost:8080/api/v1/transactions/account/${accountId}`;

  const res = http.get(url);
  check(res, { 'status was 200': (r) => r.status == 200 });
  sleep(0.1);
}
