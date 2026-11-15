'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useCategories, useWriteCategory, type ClothingCategory } from '@/lib/hooks/use-categories';
import { ApiError } from '@/lib/api';

export function PersonalCategories() {
  const t = useTranslations('personal');
  const categories = useCategories();
  const write = useWriteCategory();
  const [name, setName] = useState('');
  const [editing, setEditing] = useState<ClothingCategory>();
  return (
    <div className="max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold">{t('manageCategories')}</h1>
      <p className="text-muted-foreground">{t('categoryHelp')}</p>
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
                ? t('duplicateCategory')
                : t('saveError'),
            );
          }
        }}
      >
        <div className="flex-1 space-y-2">
          <Label htmlFor="category-name">{t('categoryName')}</Label>
          <Input
            id="category-name"
            disabled={write.isPending}
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
      {categories.isLoading && <p>{t('loading')}</p>}
      {categories.isError && <p role="alert">{t('loadError')}</p>}
      {categories.data?.length === 0 && (
        <p className="text-muted-foreground">{t('emptyCategories')}</p>
      )}
      <ul className="divide-y rounded-lg border">
        {categories.data?.map((category) => (
          <li key={category.id} className="flex items-center gap-3 p-4">
            <span className="flex-1">{category.name}</span>
            <Button
              variant="outline"
              size="sm"
              disabled={write.isPending}
              onClick={() => {
                setEditing(category);
                setName(category.name);
              }}
            >
              {t('rename')}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={write.isPending}
              onClick={async () => {
                if (!window.confirm(t('deleteCategoryConfirm'))) return;
                try {
                  await write.mutateAsync({ id: category.id, remove: true });
                  if (editing?.id === category.id) {
                    setEditing(undefined);
                    setName('');
                  }
                  toast.success(t('saved'));
                } catch (error) {
                  toast.error(
                    error instanceof ApiError && error.status === 409
                      ? t('categoryInUse')
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
