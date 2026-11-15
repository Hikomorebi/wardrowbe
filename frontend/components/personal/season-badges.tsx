'use client';

import { CalendarDays, Flower2, Leaf, Snowflake, Sun } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useSeasonLabel } from '@/lib/hooks/use-translated-constants';

const styles = {
  spring: {
    icon: Flower2,
    className: 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/50 dark:text-emerald-300',
  },
  summer: {
    icon: Sun,
    className: 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950/50 dark:text-amber-300',
  },
  fall: {
    icon: Leaf,
    className: 'border-orange-200 bg-orange-50 text-orange-800 dark:border-orange-900 dark:bg-orange-950/50 dark:text-orange-300',
  },
  winter: {
    icon: Snowflake,
    className: 'border-sky-200 bg-sky-50 text-sky-800 dark:border-sky-900 dark:bg-sky-950/50 dark:text-sky-300',
  },
};

export function SeasonBadges({ seasons }: { seasons: string[] }) {
  const t = useTranslations('personal');
  const label = useSeasonLabel();
  const values = [...new Set([...Object.keys(styles).filter((season) => seasons.includes(season)), ...seasons])];

  return (
    <div className="flex flex-wrap gap-1.5" role="group" aria-label={t('season')}>
      {values.length ? values.map((season) => {
        const style = styles[season as keyof typeof styles];
        const Icon = style?.icon ?? CalendarDays;
        return (
          <span
            key={season}
            className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${style?.className ?? 'border-border bg-muted/50 text-muted-foreground'}`}
          >
            <Icon className="h-3.5 w-3.5" aria-hidden="true" />
            {label(season)}
          </span>
        );
      }) : (
        <span className="inline-flex items-center gap-1.5 rounded-full border border-dashed px-2.5 py-1 text-xs text-muted-foreground">
          <CalendarDays className="h-3.5 w-3.5" aria-hidden="true" />
          {t('unset')}
        </span>
      )}
    </div>
  );
}
