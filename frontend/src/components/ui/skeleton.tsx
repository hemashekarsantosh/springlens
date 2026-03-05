import { cn } from "@/lib/utils";

function Skeleton({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "animate-pulse rounded-md bg-muted",
        className
      )}
      aria-busy="true"
      aria-label="Loading..."
      {...props}
    />
  );
}

export { Skeleton };
