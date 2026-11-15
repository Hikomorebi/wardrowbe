'use client';

import { useEffect, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';
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
              {t('startingWith', { name: seededItem.data.name || typeLabel(seededItem.data.type) })}
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
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">{itemId ? t('matchingOutfits') : t('outfits')}</h1>
        <Button onClick={() => setAdding(true)}>{t('createOutfit')}</Button>
      </div>
      {itemId && (
        <Link className="text-primary" href="/dashboard/personal/outfits">
          {t('allOutfits')}
        </Link>
      )}
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
          {t('emptyOutfits')}
        </p>
      )}
      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        {data?.outfits.map((outfit) => (
          <article key={outfit.id} className="rounded-xl border p-4 space-y-4 bg-card">
            <h2 className="font-semibold">{outfit.name}</h2>
            <div className="grid grid-cols-3 gap-2">
              {outfit.items.map((item) => (
                <Link key={item.id} href={`/dashboard/personal/wardrobe?item=${item.id}`}>
                  <Image
                    src={item.thumbnail_url || item.image_url || ''}
                    alt={item.name || typeLabel(item.type)}
                    width={200}
                    height={200}
                    className="aspect-square object-contain bg-muted rounded-md"
                  />
                  <p className="text-xs truncate mt-1">{item.name || typeLabel(item.type)}</p>
                </Link>
              ))}
            </div>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={() => setEditing(outfit)}>
                {t('editOutfit')}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                disabled={remove.isPending}
                onClick={() => {
                  if (window.confirm(t('deleteOutfitConfirm'))) remove.mutate(outfit.id);
                }}
              >
                {t('delete')}
              </Button>
            </div>
          </article>
        ))}
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
