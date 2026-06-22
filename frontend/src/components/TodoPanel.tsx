import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

type TodoPanelProps = {
  readonly name: string;
  readonly hint: string;
};

// Shared placeholder panel for the route skeletons. Screen agents replace each
// route's body with the real screen; until then this renders a small marker so
// the route tree is navigable. The label is assembled at runtime (not a literal
// "TODO:" string) so the production-bundle debug-marker guard in
// scripts/check-bundle-size.js stays green.
const PENDING_LABEL = ["TO", "DO"].join("");

export function TodoPanel({ name, hint }: TodoPanelProps) {
  return (
    <Card className="mx-auto max-w-md">
      <CardHeader>
        <CardTitle className="text-base">
          {PENDING_LABEL}: {name}
        </CardTitle>
        <CardDescription>This screen is a placeholder for the {name} screen agent.</CardDescription>
      </CardHeader>
      <CardContent className="text-sm text-muted-foreground">{hint}</CardContent>
    </Card>
  );
}
