import { expect, type Page } from "@playwright/test";

export const APP_ORIGIN = process.env.E2E_ORIGIN ?? "http://127.0.0.1:5173";
export const REALM_NAME = process.env.E2E_REALM ?? "oidc-service-reference";

export const KEYCLOAK_AUTH_RE = new RegExp(
  `realms\\/${REALM_NAME}\\/protocol\\/openid-connect\\/auth`
);

// Leak guard: claim names are matched as whole words (\b) so a legitimate error-code
// constant such as "INVALID_TOKEN" is not flagged, while real token claim keys and any
// JWT-shaped string still are.
export const TOKEN_MATERIAL_RE =
  /\b(?:access_token|refresh_token|id_token)\b|[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}/i;

// Canonical Flyway-seeded product TSID (V1__create_products.sql; sku MUG-001, "Starter Mug").
export const SEEDED_MUG_ID = "6801HWW000000";

export async function loginAs(
  page: Page,
  username: string,
  password: string = username
): Promise<void> {
  await page.goto("/");
  await page.getByRole("link", { name: /sign in/i }).click();
  await page.waitForURL(KEYCLOAK_AUTH_RE);
  await page.fill("#username", username);
  await page.fill("#password", password);
  await Promise.all([page.waitForURL(`${APP_ORIGIN}/`), page.click("#kc-login")]);
  // Authenticated marker: the "Sign out" button is present iff a session exists.
  await expect(page.getByRole("button", { name: /sign out/i })).toBeVisible();
}
