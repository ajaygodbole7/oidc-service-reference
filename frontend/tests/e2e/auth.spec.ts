import { expect, test } from "@playwright/test";

// Fast, backend-free smoke. Runs under `pnpm run test:e2e` (scripts/
// verify-frontend.sh) against the Vite dev server ALONE — no Keycloak /
// Valkey / BFF required. It asserts the one thing the SPA
// must get right with no backend: the anonymous landing page exposes a
// return_to-bearing sign-in entry and holds zero browser-side token state.
//
// The full authenticated flow is added after the scaffold stack is booting.
test("anonymous home shows sign-in entry without browser-side tokens", async ({
  page
}) => {
  await page.goto("/");

  await expect(page.getByRole("link", { name: /sign in/i })).toBeVisible();
  // Per return-to-login contract: a user-visible Sign in link must include
  // `return_to`; a bare `/auth/login` link is forbidden. On the anonymous
  // home page, the current route is "/" so the encoded value is "%2F".
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
