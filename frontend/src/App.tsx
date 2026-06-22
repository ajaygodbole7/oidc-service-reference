// The monolithic App is gone: catalog/cart now live behind TanStack Router
// (src/router.tsx) and TanStack Query (src/lib/queries.ts), and the shell is
// AppShell (src/components/AppShell.tsx). main.tsx mounts the RouterProvider
// directly. This module survives only as a thin convenience re-export of the
// router factory so older imports of `App` resolve to the composition root.
export { createAppRouter as App } from "@/router";
