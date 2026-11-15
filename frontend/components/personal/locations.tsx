'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useLocations, useWriteLocation, type StorageLocation } from '@/lib/hooks/use-locations';
import { ApiError } from '@/lib/api';

export function PersonalLocations() {
  const t = useTranslations('personal');
  const locations = useLocations();
  const write = useWriteLocation();
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<StorageLocation>();
  return (
    <div className="max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold">{t('manageLocations')}</h1>
      <p className="text-muted-foreground">{t('locationHelp')}</p>
      <form
        className="flex gap-3 items-end"
        onSubmit={async (event) => {
          event.preventDefault();
          try {
            await write.mutateAsync({ id: editing?.id, name: name.trim() });
            setName('');
            setEditing(undefined);
            toast.success(t('saved'));
          } catch (error) {
            toast.error(
              error instanceof ApiError && error.status === 409
                ? t('duplicateLocation')
                : t('saveError'),
            );
          }
        }}
      >
        <div className="flex-1 space-y-2">
          <Label htmlFor="location-name">{t('locationName')}</Label>
          <Input
            id="location-name"
            required
            maxLength={100}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <Button disabled={write.isPending || !name.trim()}>{editing ? t('save') : t('add')}</Button>
        {editing && (
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setEditing(undefined);
              setName('');
            }}
          >
            {t('cancel')}
          </Button>
        )}
      </form>
      {locations.isLoading && <p>{t('loading')}</p>}
      {locations.isError && <p role="alert">{t('loadError')}</p>}
      {locations.data?.length === 0 && (
        <p className="text-muted-foreground">{t('emptyLocations')}</p>
      )}
      <ul className="divide-y rounded-lg border">
        {locations.data?.map((location) => (
          <li key={location.id} className="flex items-center gap-3 p-4">
            <span className="flex-1">{location.name}</span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setEditing(location);
                setName(location.name);
              }}
            >
              {t('rename')}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={write.isPending}
              onClick={async () => {
                if (!window.confirm(t('deleteLocationConfirm'))) return;
                try {
                  await write.mutateAsync({ id: location.id, remove: true });
                  toast.success(t('saved'));
                } catch (error) {
                  toast.error(
                    error instanceof ApiError && error.status === 409
                      ? t('locationInUse')
                      : t('saveError'),
                  );
                }
              }}
            >
              {t('delete')}
            </Button>
          </li>
        ))}
      </ul>
    </div>
  );
}
