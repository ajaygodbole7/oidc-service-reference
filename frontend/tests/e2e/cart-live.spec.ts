import { expect, test, type Page } from "@playwright/test";

const APP_ORIGIN = "http://127.0.0.1:5173";
const REALM_NAME = "oidc-service-reference";
const KEYCLOAK_AUTH_RE = new RegExp(
  `realms\\/${REALM_NAME}\\/protocol\\/openid-connect\\/auth`
);

type JsonResponse = {
  readonly url: string;
  readonly status: number;
  readonly body: string;
};

type FetchResult = {
  readonly status: number;
  readonly body: string;
  readonly authenticate: string | null;
};

type FixtureEvidence = {
  readonly subject: string;
  readonly tokenFingerprint: string;
  readonly authorizationBearerPresent: boolean;
  readonly unsafeIdentityHeaderPresent: boolean;
};

const TOKEN_MATERIAL_RE =
  /access_token|refresh_token|id_token|[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}/i;

async function loginAsAlice(page: Page): Promise<void> {
  await page.goto("/");
  await page.getByRole("link", { name: /sign in/i }).click();
  await page.waitForURL(KEYCLOAK_AUTH_RE);
  await page.fill("#username", "alice");
  await page.fill("#password", "alice");
  await Promise.all([
    page.waitForURL(`${APP_ORIGIN}/`),
    page.click("#kc-login")
  ]);
  await expect(page.getByText(/signed in as/i)).toBeVisible();
}

function installSameOriginJsonCapture(page: Page): {
  readonly responses: readonly JsonResponse[];
} {
  const responses: JsonResponse[] = [];

  page.on("response", (response) => {
    const url = response.url();
    if (!url.startsWith(APP_ORIGIN)) return;
    if (!/json/i.test(response.headers()["content-type"] ?? "")) return;

    void response.text().then(
      (body) => {
        responses.push({ url, status: response.status(), body });
      },
      () => {
        responses.push({ url, status: response.status(), body: "" });
      }
    );
  });

  return { responses };
}

function joinedBodies(responses: readonly JsonResponse[]): string {
  return responses.map((response) => response.body).join("\n");
}

async function csrfHeaders(page: Page): Promise<Record<string, string>> {
  const csrf = await page.evaluate(() => {
    const cookie = document.cookie
      .split(";")
      .map((part) => part.trim())
      .find((part) => part.startsWith("XSRF-TOKEN="));
    return cookie ? decodeURIComponent(cookie.slice("XSRF-TOKEN=".length)) : "";
  });
  expect(csrf, "XSRF-TOKEN cookie must be visible to the SPA for unsafe fixture calls")
    .not.toBe("");
  return { "X-XSRF-TOKEN": csrf };
}

async function apiFetch(
  page: Page,
  path: string,
  init: RequestInit = {}
): Promise<FetchResult> {
  return page.evaluate(
    async ({ requestPath, requestInit }) => {
      const res = await fetch(requestPath, {
        credentials: "include",
        headers: { Accept: "application/json", ...(requestInit.headers ?? {}) },
        ...requestInit
      });
      return {
        status: res.status,
        body: await res.text(),
        authenticate: res.headers.get("www-authenticate")
      };
    },
    { requestPath: path, requestInit: init }
  );
}

async function fixtureEvidence(
  page: Page,
  headers: Record<string, string> = {}
): Promise<FixtureEvidence> {
  return page.evaluate(async (requestHeaders) => {
    const res = await fetch("/api/_test/cart/evidence", {
      credentials: "include",
      headers: { Accept: "application/json", ...requestHeaders }
    });
    if (!res.ok) {
      throw new Error(`fixture evidence returned ${res.status}`);
    }
    return (await res.json()) as FixtureEvidence;
  }, headers);
}

test("SEC-CART-CURRENT-USER-SPICEDB: current cart loads through APISIX without token material", async ({
  page
}) => {
  test.skip(process.env.E2E_FULL_STACK !== "1", "requires live local stack");

  const capture = installSameOriginJsonCapture(page);

  await loginAsAlice(page);
  await expect(page.getByText("Your cart is empty")).toBeVisible();

  await expect
    .poll(
      () =>
        capture.responses.some(
          (response) => response.url === `${APP_ORIGIN}/api/cart` && response.status === 200
        ),
      { message: "expected /api/cart to return 200 through the gateway" }
    )
    .toBe(true);

  expect(joinedBodies(capture.responses)).not.toMatch(TOKEN_MATERIAL_RE);
});

test("SEC-SCOPE-WITHOUT-RELATIONSHIP: Alice cannot read Bob's cart through APISIX", async ({
  page
}) => {
  test.skip(process.env.E2E_FULL_STACK !== "1", "requires live local stack");

  await loginAsAlice(page);

  const response = await apiFetch(page, "/api/carts/bob-cart");

  expect(response.status).toBe(403);
  expect(response.body).not.toMatch(TOKEN_MATERIAL_RE);
});

test("SEC-RELATIONSHIP-WITHOUT-SCOPE: Alice relationship cannot bypass missing cart scope", async ({
  page
}) => {
  test.skip(process.env.CART_SECURITY_SCENARIO !== "missing-scope", "requires missing-scope auth-service scenario");

  await loginAsAlice(page);

  const response = await apiFetch(page, "/api/cart");

  expect(response.status).toBe(403);
  expect(response.body).toContain("missing required scope cart:read");
  expect(response.body).not.toMatch(TOKEN_MATERIAL_RE);
});

test("SEC-NON-COMMERCE-AUD: non-commerce audience is rejected before cart domain work", async ({
  page
}) => {
  test.skip(process.env.CART_SECURITY_SCENARIO !== "non-commerce-aud", "requires non-commerce-aud auth-service scenario");

  await loginAsAlice(page);

  const response = await apiFetch(page, "/api/cart");

  expect(response.status).toBe(401);
  expect(response.authenticate ?? "").toContain("invalid_token");
  expect(response.body).not.toMatch(TOKEN_MATERIAL_RE);
});

test("SEC-SPOOFED-IDENTITY-HEADERS: gateway strips browser-supplied identity headers", async ({
  page
}) => {
  test.skip(process.env.CART_SECURITY_FIXTURES !== "1", "requires local test-fixture profile");

  await loginAsAlice(page);

  const evidence = await fixtureEvidence(page, {
    "X-User": "admin",
    "X-Forwarded-User": "admin",
    "X-Auth-Request-User": "admin",
    "X-Forwarded-Access-Token": "attacker-token"
  });

  expect(evidence.subject).toBe("alice");
  expect(evidence.authorizationBearerPresent).toBe(true);
  expect(evidence.unsafeIdentityHeaderPresent).toBe(false);
  expect(evidence.tokenFingerprint).toMatch(/^[a-f0-9]{16}$/);
});

test("SEC-BROWSER-AUTHORIZATION-OVERWRITTEN: gateway overwrites browser bearer", async ({
  page
}) => {
  test.skip(process.env.CART_SECURITY_FIXTURES !== "1", "requires local test-fixture profile");

  await loginAsAlice(page);

  const baseline = await fixtureEvidence(page);
  const malicious = await fixtureEvidence(page, {
    Authorization: "Bearer attacker.supplied.token"
  });

  expect(malicious.subject).toBe("alice");
  expect(malicious.authorizationBearerPresent).toBe(true);
  expect(malicious.unsafeIdentityHeaderPresent).toBe(false);
  expect(malicious.tokenFingerprint).toBe(baseline.tokenFingerprint);
});

test("SEC-RELATIONSHIP-REMOVAL-IMMEDIATE: removed SpiceDB owner relationship denies next cart read", async ({
  page
}) => {
  test.skip(process.env.CART_SECURITY_FIXTURES !== "1", "requires local test-fixture profile");

  await loginAsAlice(page);
  const csrf = await csrfHeaders(page);

  try {
    const restoreSeed = await apiFetch(page, "/api/_test/cart/relationships/local-seed", {
      method: "POST",
      headers: csrf
    });
    expect(restoreSeed.status).toBe(200);

    const beforeRemoval = await apiFetch(page, "/api/cart");
    expect(beforeRemoval.status).toBe(200);

    const remove = await apiFetch(page, "/api/_test/cart/relationships/alice-cart-owner", {
      method: "DELETE",
      headers: csrf
    });
    expect(remove.status).toBe(200);

    const afterRemoval = await apiFetch(page, "/api/cart");
    expect(afterRemoval.status).toBe(403);
    expect(afterRemoval.body).not.toMatch(TOKEN_MATERIAL_RE);
  } finally {
    await apiFetch(page, "/api/_test/cart/relationships/alice-cart-owner", {
      method: "POST",
      headers: csrf
    });
  }
});
