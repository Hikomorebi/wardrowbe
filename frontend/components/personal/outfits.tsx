'use client';

import { useEffect, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';
import { ArrowLeft, ArrowUpRight, Layers3, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { ItemPicker } from '@/components/shared/item-picker';
import { useOutfits } from '@/lib/hooks/use-outfits';
import { useCreateStudioOutfit, usePatchOutfit } from '@/lib/hooks/use-studio';
import { useItem } from '@/lib/hooks/use-items';
import { useTypeLabel } from '@/lib/hooks/use-translated-constants';
import { invalidateOutfitCaches } from '@/lib/hooks/cache-invalidation';
import { api } from '@/lib/api';
import type { Outfit } from '@/lib/types';

function OutfitEditor({
  outfit,
  seed,
  close,
}: {
  outfit?: Outfit;
  seed?: string;
  close: () => void;
}) {
  const t = useTranslations('personal');
  const typeLabel = useTypeLabel();
  const [name, setName] = useState(outfit?.name ?? '');
  const [ids, setIds] = useState(new Set(outfit?.items.map((item) => item.id) ?? []));
  const seededItem = useItem(seed ?? '');
  useEffect(() => {
    if (seededItem.data && !seededItem.data.is_archived && seededItem.data.status === 'ready')
      setIds(new Set([seededItem.data.id]));
  }, [seededItem.data]);
  const create = useCreateStudioOutfit();
  const patch = usePatchOutfit();
  const busy = create.isPending || patch.isPending;
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) close();
      }}
    >
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{outfit ? t('editOutfit') : t('createOutfit')}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={async (event) => {
            event.preventDefault();
            try {
              if (outfit)
                await patch.mutateAsync({
                  id: outfit.id,
                  payload: { name: name.trim(), items: Array.from(ids) },
                });
              else
                await create.mutateAsync({
                  name: name.trim(),
                  items: Array.from(ids),
                  occasion: 'casual',
                  mark_worn: false,
                  scheduled_for: null,
                });
              toast.success(t('saved'));
              close();
            } catch {
              toast.error(t('saveError'));
            }
          }}
        >
          <Label htmlFor="outfit-name">{t('name')}</Label>
          <Input
            id="outfit-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            maxLength={100}
          />
          <p className="text-sm text-muted-foreground">{t('selectedItems', { count: ids.size })}</p>
          {seededItem.data && (
            <p className="text-sm">
              {t('startingWith', { name: seededItem.data.name || seededItem.data.category_name || typeLabel(seededItem.data.type) })}
            </p>
          )}
          <ItemPicker
            selectedIds={ids}
            hideNeedsWash={false}
            onToggle={(item) => {
              if (item.status !== 'ready') return;
              setIds((previous) => {
                const next = new Set(previous);
                if (next.has(item.id)) next.delete(item.id);
                else if (next.size < 20) next.add(item.id);
                return next;
              });
            }}
          />
          <div className="flex justify-end gap-2">
            <Button variant="outline" type="button" disabled={busy} onClick={close}>
              {t('cancel')}
            </Button>
            <Button disabled={busy || ids.size === 0 || !name.trim()}>
              {busy ? t('saving') : t('save')}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function PersonalOutfits() {
  const t = useTranslations('personal');
  const typeLabel = useTypeLabel();
  const params = useSearchParams();
  const router = useRouter();
  const qc = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<Outfit>();
  const [page, setPage] = useState(1);
  const itemId = params.get('item') || undefined;
  const seed = params.get('seed') || undefined;
  const sourceItem = useItem(itemId ?? '');
  const { data, isLoading, isError, refetch } = useOutfits(
    { source: 'manual', is_lookbook: true, item_id: itemId },
    page,
    24,
  );
  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/outfits/${id}`),
    onSuccess: () => {
      invalidateOutfitCaches(qc);
      toast.success(t('saved'));
    },
    onError: () => toast.error(t('saveError')),
  });
  const close = () => {
    setAdding(false);
    setEditing(undefined);
    if (seed) router.replace('/dashboard/personal/outfits');
  };
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-2xl font-bold">{itemId ? t('matchingOutfits') : t('outfits')}</h1>
          {itemId && <p className="text-sm text-muted-foreground">{t('matchingHelp')}</p>}
        </div>
        {itemId ? (
          <Button asChild variant="outline" className="shrink-0 gap-2 rounded-xl">
            <Link href="/dashboard/personal/outfits">
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              {t('outfits')}
            </Link>
          </Button>
        ) : (
          <Button className="gap-2 rounded-xl" onClick={() => setAdding(true)}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            {t('createOutfit')}
          </Button>
        )}
      </div>
      {itemId && sourceItem.data && (
        <div className="flex items-center gap-4 rounded-2xl border bg-muted/30 p-4">
          <Image
            src={sourceItem.data.thumbnail_url || sourceItem.data.image_url || ''}
            alt={sourceItem.data.name || sourceItem.data.category_name || t('unset')}
            width={80}
            height={80}
            className="h-20 w-20 shrink-0 rounded-xl border bg-background object-contain"
          />
          <div className="min-w-0 space-y-1">
            <p className="text-xs font-medium text-muted-foreground">{t('currentItem')}</p>
            <p className="truncate font-semibold">{sourceItem.data.name || sourceItem.data.category_name || t('unset')}</p>
            <p className="text-sm text-muted-foreground">{sourceItem.data.category_name || t('unset')}</p>
          </div>
        </div>
      )}
      {itemId && sourceItem.isError && <p role="alert">{t('loadError')}</p>}
      {isLoading && <p>{t('loading')}</p>}
      {isError && (
        <div role="alert">
          <p>{t('loadError')}</p>
          <Button variant="outline" onClick={() => refetch()}>
            {t('retry')}
          </Button>
        </div>
      )}
      {!isLoading && !isError && !data?.outfits.length && (
        <p className="border border-dashed rounded-lg p-12 text-center text-muted-foreground">
          {itemId ? t('emptyMatchingOutfits') : t('emptyOutfits')}
        </p>
      )}
      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        {data?.outfits.map((outfit) => {
          const shownItems = itemId ? outfit.items.filter((item) => item.id !== itemId) : outfit.items;
          const itemColumns = itemId
            ? (shownItems.length === 1 ? 'grid-cols-1' : 'grid-cols-2')
            : 'grid-cols-2 sm:grid-cols-3';
          return (
            <article key={outfit.id} className="space-y-4 rounded-2xl border bg-card p-4 shadow-sm">
              <h2 className="flex items-center gap-2 font-semibold">
                <Layers3 className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                {outfit.name}
              </h2>
              <div className={`grid gap-3 ${itemColumns}`}>
                {shownItems.map((item) => (
                  <Link
                    key={item.id}
                    href={`/dashboard/personal/wardrobe?item=${item.id}`}
                    aria-label={item.name || item.category_name || typeLabel(item.type)}
                    className="group min-w-0 rounded-xl border bg-muted/20 p-2 transition-colors hover:border-primary/40 hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                  >
                    <Image
                      src={item.thumbnail_url || item.image_url || ''}
                      alt={item.name || item.category_name || typeLabel(item.type)}
                      width={200}
                      height={200}
                      className="aspect-square w-full rounded-lg bg-background object-contain"
                    />
                    <div className="mt-2 flex items-center gap-1">
                      <p className="min-w-0 flex-1 truncate text-xs font-medium">{item.name || item.category_name || typeLabel(item.type)}</p>
                      <ArrowUpRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground group-hover:text-primary" aria-hidden="true" />
                    </div>
                    {item.category_name && <p className="mt-0.5 truncate text-xs text-muted-foreground">{item.category_name}</p>}
                  </Link>
                ))}
              </div>
              {itemId && shownItems.length === 0 && (
                <p className="rounded-xl bg-muted/40 p-3 text-sm text-muted-foreground">{t('noCompanionItems')}</p>
              )}
              {!itemId && (
                <div className="flex gap-2 border-t pt-3">
                  <Button size="sm" variant="outline" className="rounded-lg" onClick={() => setEditing(outfit)}>
                    {t('editOutfit')}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    className="rounded-lg text-destructive hover:text-destructive"
                    disabled={remove.isPending}
                    onClick={() => {
                      if (window.confirm(t('deleteOutfitConfirm'))) remove.mutate(outfit.id);
                    }}
                  >
                    {t('delete')}
                  </Button>
                </div>
              )}
            </article>
          );
        })}
      </div>
      <div className="flex justify-center gap-3 items-center">
        <Button variant="outline" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
          {t('previous')}
        </Button>
        <span>{page}</span>
        <Button variant="outline" disabled={!data?.has_more} onClick={() => setPage((p) => p + 1)}>
          {t('next')}
        </Button>
      </div>
      {(adding || editing || seed) && (
        <OutfitEditor
          key={editing?.id || seed || 'new'}
          outfit={editing}
          seed={seed}
          close={close}
        />
      )}
    </div>
  );
}
