'use client';

import { useTranslations } from 'next-intl';
import { useCategories } from '@/lib/hooks/use-categories';

export function CategorySelect({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const t = useTranslations('personal');
  const { data: categories = [], isLoading, isError } = useCategories();
  return (
    <div className="space-y-1">
      <select aria-label={t('category')} className="h-10 rounded-md border border-input bg-background px-3 text-sm w-full" value={value} disabled={isLoading || isError} onChange={(e) => onChange(e.target.value)}>
        <option value="">{t('unset')}</option>
        {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
      </select>
      {isError && <p role="alert">{t('loadError')}</p>}
    </div>
  );
}
