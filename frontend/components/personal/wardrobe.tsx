'use client';

import { useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useSearchParams, useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { AddItemDialog } from '@/components/add-item-dialog';
import { ItemDetailDialog } from '@/components/item-detail-dialog';
import { ClothingCard } from '@/components/personal/clothing-card';
import { api, ApiError } from '@/lib/api';
import { useItems, useItem, useCreateItem, useReplaceItemImage } from '@/lib/hooks/use-items';
import { useCategories } from '@/lib/hooks/use-categories';
import { useLocations } from '@/lib/hooks/use-locations';
import { useSeasonLabel } from '@/lib/hooks/use-translated-constants';
import { queryKeys } from '@/lib/hooks/query-keys';
import { invalidatePrimaryImageQueries } from '@/lib/hooks/cache-invalidation';
import { ACCEPTED_IMAGE_INPUT } from '@/lib/image-types';
import type { Item } from '@/lib/types';

export const CONDITIONS = ['excellent', 'good', 'fair', 'poor'] as const;
export const SEASONS = ['spring', 'summer', 'fall', 'winter'] as const;
export const selectClass = 'h-10 rounded-md border border-input bg-background px-3 text-sm w-full';

function ItemEditor({
  item,
  close,
  showPhotos,
}: {
  item?: Item;
  close: () => void;
  showPhotos: () => void;
}) {
  const t = useTranslations('personal');
  const seasonLabel = useSeasonLabel();
  const { data: categories = [] } = useCategories();
  const { data: locations = [] } = useLocations();
  const create = useCreateItem();
  const replace = useReplaceItemImage();
  const qc = useQueryClient();
  const [preview, setPreview] = useState(item?.image_url ?? '');
  const [file, setFile] = useState<File>();
  const [name, setName] = useState(item?.name ?? '');
  const [category, setCategory] = useState(item?.category_id ?? '');
  const [condition, setCondition] = useState(item?.condition ?? '');
  const [location, setLocation] = useState(item?.storage_location_id ?? '');
  const [seasons, setSeasons] = useState<string[]>(item?.season ?? item?.tags.season ?? []);
  const [notes, setNotes] = useState(item?.notes ?? '');
  const save = useMutation({
    mutationFn: async () => {
      if (item) {
        await api.patch(`/items/${item.id}`, {
          name: name || null,
          category_id: category || null,
          condition: condition || null,
          storage_location_id: location || null,
          season: seasons,
          notes: notes || null,
        });
        if (file) await replace.mutateAsync({ itemId: item.id, file });
        invalidatePrimaryImageQueries(qc, item.id);
      } else {
        if (!file) throw new Error('image required');
        const body = new FormData();
        body.append('image', file);
        if (category) body.append('category_id', category);
        body.append('skip_ai', 'true');
        if (name) body.append('name', name);
        if (condition) body.append('condition', condition);
        if (location) body.append('storage_location_id', location);
        if (notes) body.append('notes', notes);
        seasons.forEach((s) => body.append('season', s));
        await create.mutateAsync(body);
      }
      await qc.invalidateQueries({ queryKey: queryKeys.items.all });
    },
    onSuccess: () => {
      toast.success(t('saved'));
      close();
    },
    onError: () => toast.error(t('saveError')),
  });
  return (
    <>
      <Dialog
        open
        onOpenChange={(open) => {
          if (!open && !save.isPending) close();
        }}
      >
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{item ? t('editItem') : t('addItem')}</DialogTitle>
          </DialogHeader>
          <form
            className="space-y-5"
            onSubmit={(event) => {
              event.preventDefault();
              save.mutate();
            }}
          >
            <div className="grid gap-6 md:grid-cols-2">
              <div className="space-y-3">
                {preview && (
                  <Image
                    src={preview}
                    alt={name || t('image')}
                    width={600}
                    height={600}
                    className="aspect-square rounded-lg object-contain bg-muted w-full"
                  />
                )}
                <Label htmlFor="personal-image">{t('image')}</Label>
                <Input
                  id="personal-image"
                  type="file"
                  accept={ACCEPTED_IMAGE_INPUT}
                  required={!item}
                  onChange={(event) => {
                    const chosen = event.target.files?.[0];
                    setFile(chosen);
                    if (chosen) {
                      const reader = new FileReader();
                      reader.onload = () => setPreview(String(reader.result));
                      reader.readAsDataURL(chosen);
                    }
                  }}
                />
                {item && (
                  <Button type="button" variant="outline" onClick={showPhotos}>
                    {t('photosAndDetails')}
                  </Button>
                )}
              </div>
              <div className="space-y-3">
                <div className="space-y-1">
                  <Label htmlFor="personal-name">{t('name')}</Label>
                  <Input
                    id="personal-name"
                    maxLength={100}
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="personal-type">{t('category')}</Label>
                  <select
                    id="personal-type"
                    className={selectClass}
                    value={category}
                    required={!item}
                    onChange={(e) => setCategory(e.target.value)}
                  >
                    <option value="">{item ? t('unset') : t('choose')}</option>
                    {categories.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.name}
                      </option>
                    ))}
                  </select>
                  <Link className="text-sm text-primary" href="/dashboard/personal/categories">
                    {t('manageCategories')}
                  </Link>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="personal-condition">{t('condition')}</Label>
                  <select
                    id="personal-condition"
                    className={selectClass}
                    value={condition}
                    onChange={(e) => setCondition(e.target.value as typeof condition)}
                  >
                    <option value="">{t('unset')}</option>
                    {CONDITIONS.map((c) => (
                      <option key={c} value={c}>
                        {t(`conditions.${c}`)}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="personal-location">{t('location')}</Label>
                  <select
                    id="personal-location"
                    className={selectClass}
                    value={location}
                    onChange={(e) => setLocation(e.target.value)}
                  >
                    <option value="">{t('unset')}</option>
                    {locations.map((l) => (
                      <option key={l.id} value={l.id}>
                        {l.name}
                      </option>
                    ))}
                  </select>
                  <Link className="text-sm text-primary" href="/dashboard/personal/locations">
                    {t('manageLocations')}
                  </Link>
                </div>
                <fieldset>
                  <legend className="text-sm font-medium mb-2">{t('season')}</legend>
                  <div className="flex flex-wrap gap-3">
                    {SEASONS.map((s) => (
                      <label key={s} className="text-sm flex gap-1 items-center">
                        <input
                          type="checkbox"
                          checked={seasons.includes(s)}
                          onChange={(e) =>
                            setSeasons(
                              e.target.checked ? [...seasons, s] : seasons.filter((v) => v !== s),
                            )
                          }
                        />
                        {seasonLabel(s)}
                      </label>
                    ))}
                  </div>
                </fieldset>
              </div>
            </div>
            <div className="space-y-1">
              <Label htmlFor="personal-notes">{t('notes')}</Label>
              <Input id="personal-notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>
            <div className="flex gap-2 justify-end">
              <Button type="button" variant="outline" disabled={save.isPending} onClick={close}>
                {t('cancel')}
              </Button>
              <Button disabled={save.isPending || (!item && !file)}>
                {save.isPending ? t('saving') : t('save')}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}

export function PersonalWardrobe() {
  const t = useTranslations('personal');
  const seasonLabel = useSeasonLabel();
  const { data: categories = [] } = useCategories();
  const { data: locations = [] } = useLocations();
  const params = useSearchParams();
  const router = useRouter();
  const qc = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [bulk, setBulk] = useState(false);
  const [advanced, setAdvanced] = useState(false);
  const [page, setPage] = useState(1);
  const itemId = params.get('item') ?? '';
  const selected = useItem(itemId);
  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params.toString());
    next.delete('item');
    if (value) next.set(key, value);
    else next.delete(key);
    setPage(1);
    router.replace(`/dashboard/personal/wardrobe?${next}`, { scroll: false });
  };
  const close = () => {
    setAdding(false);
    const next = new URLSearchParams(params.toString());
    next.delete('item');
    router.replace(`/dashboard/personal/wardrobe?${next}`, { scroll: false });
  };
  const { data, isLoading, isError, refetch } = useItems(
    {
      search: params.get('search') || undefined,
      category_id: params.get('category') || undefined,
      condition: (params.get('condition') || undefined) as Item['condition'],
      storage_location_id: params.get('location') || undefined,
      season: params.get('season') || undefined,
      is_archived: params.get('archived') === 'true',
    },
    page,
    24,
  );
  const action = useMutation({
    mutationFn: async ({ item, remove }: { item: Item; remove?: boolean }) => {
      if (remove) await api.delete(`/items/${item.id}`);
      else await api.post(`/items/${item.id}/${item.is_archived ? 'restore' : 'archive'}`, {});
      invalidatePrimaryImageQueries(qc, item.id);
    },
    onSuccess: () => toast.success(t('saved')),
    onError: (error) =>
      toast.error(
        error instanceof ApiError && error.status === 409 ? t('itemInOutfit') : t('saveError'),
      ),
  });
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{t('wardrobe')}</h1>
          <p className="text-sm text-muted-foreground">
            {t('itemCount', { count: data?.total ?? 0 })}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setBulk(true)}>
            {t('bulkImport')}
          </Button>
          <Button onClick={() => setAdding(true)}>{t('addItem')}</Button>
        </div>
      </div>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
        <Input
          aria-label={t('search')}
          placeholder={t('search')}
          value={params.get('search') ?? ''}
          onChange={(e) => setFilter('search', e.target.value)}
        />
        <select
          aria-label={t('category')}
          className={selectClass}
          value={params.get('category') ?? ''}
          onChange={(e) => setFilter('category', e.target.value)}
        >
          <option value="">{t('allCategories')}</option>
          {categories.map((v) => (
            <option key={v.id} value={v.id}>
              {v.name}
            </option>
          ))}
        </select>
        <select
          aria-label={t('condition')}
          className={selectClass}
          value={params.get('condition') ?? ''}
          onChange={(e) => setFilter('condition', e.target.value)}
        >
          <option value="">{t('allConditions')}</option>
          {CONDITIONS.map((c) => (
            <option key={c} value={c}>
              {t(`conditions.${c}`)}
            </option>
          ))}
        </select>
        <select
          aria-label={t('location')}
          className={selectClass}
          value={params.get('location') ?? ''}
          onChange={(e) => setFilter('location', e.target.value)}
        >
          <option value="">{t('allLocations')}</option>
          {locations.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name}
            </option>
          ))}
        </select>
        <select
          aria-label={t('season')}
          className={selectClass}
          value={params.get('season') ?? ''}
          onChange={(e) => setFilter('season', e.target.value)}
        >
          <option value="">{t('allSeasons')}</option>
          {SEASONS.map((s) => (
            <option key={s} value={s}>
              {seasonLabel(s)}
            </option>
          ))}
        </select>
        <select
          aria-label={t('archive')}
          className={selectClass}
          value={params.get('archived') ?? ''}
          onChange={(e) => setFilter('archived', e.target.value)}
        >
          <option value="">{t('activeItems')}</option>
          <option value="true">{t('archivedItems')}</option>
        </select>
      </div>
      {isLoading && <p>{t('loading')}</p>}
      {isError && (
        <div role="alert">
          <p>{t('loadError')}</p>
          <Button variant="outline" onClick={() => refetch()}>
            {t('retry')}
          </Button>
        </div>
      )}
      {!isLoading && !isError && !data?.items.length && (
        <p className="rounded-lg border border-dashed p-12 text-center text-muted-foreground">
          {t('emptyWardrobe')}
        </p>
      )}
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
        {data?.items.map((item) => (
          <ClothingCard
            key={item.id}
            item={item}
            locationName={locations.find((location) => location.id === item.storage_location_id)?.name}
            busy={action.isPending}
            onOpen={() =>
              router.replace(
                `/dashboard/personal/wardrobe?${new URLSearchParams({ ...Object.fromEntries(params), item: item.id })}`,
                { scroll: false },
              )
            }
            onArchive={() => action.mutate({ item })}
            onDelete={() => {
              if (window.confirm(t('deleteItemConfirm'))) action.mutate({ item, remove: true });
            }}
          />
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
      {(adding || (selected.data && !advanced)) && (
        <ItemEditor
          key={adding ? 'new' : selected.data?.id}
          item={adding ? undefined : selected.data}
          close={close}
          showPhotos={() => setAdvanced(true)}
        />
      )}
      {bulk && <AddItemDialog open onOpenChange={setBulk} initialTab="bulk" />}
      {advanced && selected.data && (
        <ItemDetailDialog
          item={selected.data}
          open
          onOpenChange={(open) => {
            if (!open) {
              setAdvanced(false);
              close();
            }
          }}
        />
      )}
      {itemId && selected.isError && <p role="alert">{t('loadError')}</p>}
    </div>
  );
}
