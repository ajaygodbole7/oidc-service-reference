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

  it("accepts a valid empty order list response with no nextCursor", async () => {
    vi.mocked(callApi).mockResolvedValue(Response.json({ items: [] }));
    const page = await fetchOrders(new AbortController().signal);
    expect(page).toEqual({ items: [] });
  });

  it("rejects a null nextCursor — backend must omit the field, not send null", async () => {
    vi.mocked(callApi).mockResolvedValue(Response.json({ items: [], nextCursor: null }));
    await expect(fetchOrders(new AbortController().signal)).rejects.toThrow(
      "Orders response had an unexpected shape"
    );
  });
});
