export function FilingFooter({ recordNumber }: { recordNumber?: string }) {
  const number = recordNumber?.trim();
  if (!number) return null;

  return (
    <footer className="px-4 pt-4 pb-24 lg:pb-4 text-center text-xs text-muted-foreground">
      <a
        href="https://beian.miit.gov.cn/"
        target="_blank"
        rel="noopener noreferrer"
        className="hover:underline"
      >
        {number}
      </a>
    </footer>
  );
}
