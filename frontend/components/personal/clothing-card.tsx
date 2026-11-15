'use client';

import Image from 'next/image';
import Link from 'next/link';
import { Archive, ArchiveRestore, ArrowRight, BadgeCheck, Layers3, MapPin, Pencil, Shirt, Tag, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { SeasonBadges } from '@/components/personal/season-badges';
import type { Item } from '@/lib/types';

const conditionStyles = {
  excellent: 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/50 dark:text-emerald-300',
  good: 'border-teal-200 bg-teal-50 text-teal-800 dark:border-teal-900 dark:bg-teal-950/50 dark:text-teal-300',
  fair: 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950/50 dark:text-amber-300',
  poor: 'border-rose-200 bg-rose-50 text-rose-800 dark:border-rose-900 dark:bg-rose-950/50 dark:text-rose-300',
};

export function ClothingCard({ item, locationName, busy, onOpen, onArchive, onDelete }: {
  item: Item;
  locationName?: string;
  busy: boolean;
  onOpen: () => void;
  onArchive: () => void;
  onDelete: () => void;
}) {
  const t = useTranslations('personal');
  const name = item.name || item.category_name || t('unset');
  const image = item.medium_url || item.image_url || item.thumbnail_url;
  const ArchiveIcon = item.is_archived ? ArchiveRestore : Archive;

  return (
    <article className="group flex h-full flex-col overflow-hidden rounded-2xl border bg-card shadow-sm transition-shadow hover:shadow-md">
      <button
        type="button"
        className="relative block aspect-[4/3] w-full overflow-hidden border-b bg-gradient-to-br from-muted/30 to-muted/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-primary"
        aria-label={`${t('editItem')}：${name}`}
        onClick={onOpen}
      >
        {image ? (
          <Image
            src={image}
            alt={name}
            fill
            sizes="(min-width: 1536px) 25vw, (min-width: 1280px) 33vw, (min-width: 640px) 50vw, 100vw"
            className="object-contain p-5 transition-transform duration-300 group-hover:scale-[1.03] motion-reduce:transform-none motion-reduce:transition-none"
          />
        ) : (
          <Shirt className="absolute inset-0 m-auto h-16 w-16 text-muted-foreground/40" aria-hidden="true" />
        )}
        <span className="absolute left-3 top-3 inline-flex max-w-[calc(100%-1.5rem)] items-center gap-1.5 rounded-full border bg-background/95 px-2.5 py-1 text-xs font-medium shadow-sm">
          <Tag className="h-3 w-3 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span className="truncate">{item.category_name || t('unset')}</span>
        </span>
        <span className="absolute bottom-3 right-3 inline-flex items-center gap-1.5 rounded-full border bg-background/95 px-2.5 py-1 text-xs font-medium shadow-sm">
          <Pencil className="h-3 w-3" aria-hidden="true" />
          {t('editItem')}
        </span>
      </button>

      <div className="flex flex-1 flex-col gap-4 p-4 sm:p-5">
        <h2 className="line-clamp-2 text-base font-semibold leading-snug" title={name}>{name}</h2>
        <dl className="space-y-2.5 text-sm">
          <div className="flex items-center justify-between gap-3">
            <dt className="inline-flex shrink-0 items-center gap-1.5 text-muted-foreground">
              <BadgeCheck className="h-4 w-4" aria-hidden="true" />
              {t('condition')}
            </dt>
            <dd className={`rounded-md border px-2 py-0.5 text-xs font-medium ${item.condition ? conditionStyles[item.condition] : 'border-border bg-muted/40 text-muted-foreground'}`}>
              {item.condition ? t(`conditions.${item.condition}`) : t('unset')}
            </dd>
          </div>
          <div className="flex items-center justify-between gap-3">
            <dt className="inline-flex shrink-0 items-center gap-1.5 text-muted-foreground">
              <MapPin className="h-4 w-4" aria-hidden="true" />
              {t('location')}
            </dt>
            <dd className="min-w-0 truncate font-medium" title={locationName || t('unset')}>
              {locationName || t('unset')}
            </dd>
          </div>
        </dl>
        <div className="mt-auto space-y-2">
          <p className="text-xs text-muted-foreground">{t('season')}</p>
          <SeasonBadges seasons={item.season ?? item.tags.season ?? []} />
        </div>
      </div>

      <div className="space-y-2 border-t bg-muted/20 p-3">
        <Button asChild className="w-full justify-between gap-2 rounded-xl">
          <Link href={`/dashboard/personal/outfits?item=${item.id}`}>
            <Layers3 className="h-4 w-4" aria-hidden="true" />
            <span className="flex-1 text-center">{t('matchingOutfits')}</span>
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </Button>
        <div className="grid grid-cols-2 gap-2">
          <Button size="sm" variant="outline" className="gap-2 rounded-xl" disabled={busy} onClick={onArchive}>
            <ArchiveIcon className="h-4 w-4" aria-hidden="true" />
            {item.is_archived ? t('restore') : t('archive')}
          </Button>
          <Button size="sm" variant="outline" className="gap-2 rounded-xl border-rose-200 text-rose-700 hover:border-rose-300 hover:bg-rose-50 hover:text-rose-800 dark:border-rose-900 dark:text-rose-300 dark:hover:bg-rose-950/50 dark:hover:text-rose-200" disabled={busy} onClick={onDelete}>
            <Trash2 className="h-4 w-4" aria-hidden="true" />
            {t('delete')}
          </Button>
        </div>
      </div>
    </article>
  );
}
