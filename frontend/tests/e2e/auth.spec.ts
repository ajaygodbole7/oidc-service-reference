import {
  expect,
  test,
  type BrowserContext,
  type Cookie,
  type Page
} from "@playwright/test";
import { APP_ORIGIN, KEYCLOAK_AUTH_RE, loginAs } from "./helpers";

type BrowserStorageState = {
  readonly localStorage: Record<string, string>;
  readonly sessionStorage: Record<string, string>;
  readonly cookieHeader: string;
  readonly indexedDBNames: readonly string[];
};

function looksLikeJws(value: string): boolean {
  return /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(value);
}

function looksLikeJwe(value: string): boolean {
  return /^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+){4}$/.test(value);
}

function looksLikeOpaqueToken(value: string): boolean {
  return value.length > 200 && /^[A-Za-z0-9_.-]+$/.test(value);
}

function looksLikeTokenName(value: string): boolean {
  return /access_token|refresh_token|id_token/i.test(value);
}

async function assertNoBrowserTokens(
  page: Page,
  context: BrowserContext
): Promise<void> {
  const browserState = await page.evaluate(
    async (): Promise<BrowserStorageState> => {
      const localStorageValues: Record<string, string> = {};
      for (let i = 0; i < localStorage.length; i += 1) {
        const key = localStorage.key(i);
        if (key) localStorageValues[key] = localStorage.getItem(key) ?? "";
      }

      const sessionStorageValues: Record<string, string> = {};
      for (let i = 0; i < sessionStorage.length; i += 1) {
        const key = sessionStorage.key(i);
        if (key) sessionStorageValues[key] = sessionStorage.getItem(key) ?? "";
      }

      let indexedDBNames: string[] = [];
      if (typeof indexedDB.databases === "function") {
        try {
          const dbs = await indexedDB.databases();
          indexedDBNames = dbs.map((d) => d.name ?? "").filter(Boolean);
        } catch {
          indexedDBNames = [];
        }
      }

      return {
        localStorage: localStorageValues,
        sessionStorage: sessionStorageValues,
        cookieHeader: document.cookie,
        indexedDBNames
      };
    }
  );

  for (const [key, value] of Object.entries(browserState.localStorage)) {
    expect(looksLikeJws(value), `localStorage[${key}] looks like JWS`).toBeFalsy();
    expect(looksLikeJwe(value), `localStorage[${key}] looks like JWE`).toBeFalsy();
    expect(looksLikeOpaqueToken(value), `localStorage[${key}] looks token-like`).toBeFalsy();
    expect(looksLikeTokenName(`${key}${value}`), `localStorage[${key}] names token material`).toBeFalsy();
  }

  for (const [key, value] of Object.entries(browserState.sessionStorage)) {
    expect(looksLikeJws(value), `sessionStorage[${key}] looks like JWS`).toBeFalsy();
    expect(looksLikeJwe(value), `sessionStorage[${key}] looks like JWE`).toBeFalsy();
    expect(looksLikeOpaqueToken(value), `sessionStorage[${key}] looks token-like`).toBeFalsy();
    expect(looksLikeTokenName(`${key}${value}`), `sessionStorage[${key}] names token material`).toBeFalsy();
  }

  expect(browserState.indexedDBNames).toEqual([]);
  expect(browserState.cookieHeader).not.toMatch(/access_token|refresh_token|id_token/i);

  for (const part of browserState.cookieHeader.split(";")) {
    const value = part.split("=").slice(1).join("=").trim();
    if (!value) continue;
    expect(looksLikeJws(value), `document.cookie value looks like JWS: ${part}`).toBeFalsy();
    expect(looksLikeJwe(value), `document.cookie value looks like JWE: ${part}`).toBeFalsy();
  }

  const appHost = new URL(APP_ORIGIN).hostname;
  const appCookies = (await context.cookies()).filter(
    (cookie: Cookie) => cookie.domain === appHost || cookie.domain === `.${appHost}`
  );

  for (const cookie of appCookies) {
    expect(looksLikeTokenName(cookie.name), `cookie name looks token-like: ${cookie.name}`).toBeFalsy();
    expect(looksLikeJws(cookie.value), `cookie ${cookie.name} value looks like JWS`).toBeFalsy();
    expect(looksLikeJwe(cookie.value), `cookie ${cookie.name} value looks like JWE`).toBeFalsy();
    expect(looksLikeOpaqueToken(cookie.value), `cookie ${cookie.name} value looks token-like`).toBeFalsy();
  }

  const sid = appCookies.find((cookie) => cookie.name === "sid" || cookie.name === "__Host-sid");
  if (sid) {
    expect(sid.httpOnly, "sid must be HttpOnly").toBe(true);
    expect(sid.sameSite).toBe("Lax");
  }
}

function installResponseBodyTokenGuard(page: Page): { assertClean: () => Promise<void> } {
  const pending: Promise<string | null>[] = [];

  page.on("response", (response) => {
    const url = response.url();
    if (!url.startsWith(APP_ORIGIN)) return;
    if (!/json/i.test(response.headers()["content-type"] ?? "")) return;

    pending.push(
      response.text().then(
        (body): string | null => {
          if (/access_token|refresh_token|id_token/i.test(body)) {
            return `${url} names a token field`;
          }
          if (/[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}/.test(body)) {
            return `${url} carries a JWS-shaped string`;
          }
          return null;
        },
        () => null
      )
    );
  });

  return {
    assertClean: async () => {
      const results = await Promise.all(pending);
      expect(
        pending.length,
        "body-token guard must observe at least one same-origin JSON response"
      ).toBeGreaterThan(0);
      expect(
        results.filter((value): value is string => value !== null),
        "same-origin JSON bodies must carry no token material"
      ).toEqual([]);
    }
  };
}

async function loginAsAlice(page: Page): Promise<void> {
  await loginAs(page, "alice");
}

test("anonymous home shows sign-in entry without browser-side tokens", async ({
  page
}) => {
  await page.goto("/");

  await expect(page.getByRole("link", { name: /sign in/i })).toBeVisible();
  await expect(page.getByTestId("sign-in-link")).toHaveAttribute(
    "href",
    `/auth/login?return_to=${encodeURIComponent("/")}`
  );

  const browserState = await page.evaluate(() => ({
    localStorageKeys: Object.keys(localStorage),
    sessionStorageKeys: Object.keys(sessionStorage),
    cookieHeader: document.cookie
  }));
  expect(browserState.localStorageKeys).toEqual([]);
  expect(browserState.sessionStorageKeys).toEqual([]);
  expect(browserState.cookieHeader).not.toMatch(/access_token|refresh_token|id_token/i);
});

test("SEC-NO-BROWSER-TOKENS: authenticated BFF flow exposes no token material", async ({
  page,
  context
}) => {
  test.skip(
    process.env.E2E_FULL_STACK !== "1",
    "requires Keycloak, Valkey, Auth Service, APISIX, and Vite"
  );

  const responseGuard = installResponseBodyTokenGuard(page);

  await loginAsAlice(page);
  await assertNoBrowserTokens(page, context);
  await responseGuard.assertClean();
});
