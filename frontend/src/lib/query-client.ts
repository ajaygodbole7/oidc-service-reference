import { QueryClient } from "@tanstack/react-query";

// client-swr-dedup: one shared QueryClient for the SPA. Defaults tuned for a
// storefront read model — a short staleTime dedupes the burst of reads on
// navigation, and window-focus refetch is off (this is not a live dashboard;
// catalog/cart don't need to refetch every time the tab regains focus).
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: 1
    }
  }
});
