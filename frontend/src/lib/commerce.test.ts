import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/auth", () => ({
  callApi: vi.fn()
}));

import { callApi } from "@/auth";
import { fetchOrders } from "@/lib/commerce";

describe("commerce order fetchers", () => {
  beforeEach(() => {
    vi.mocked(callApi).mockReset();
  });

  it("normalizes a null order-history nextCursor from the backend", async () => {
    vi.mocked(callApi).mockResolvedValue(Response.json({ items: [], nextCursor: null }));

    const page = await fetchOrders(new AbortController().signal);

    expect(page).toEqual({ items: [] });
  });
});
