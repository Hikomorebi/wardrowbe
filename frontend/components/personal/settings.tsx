'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { signOut } from 'next-auth/react';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/hooks/use-auth';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { LocaleSwitcher } from '@/components/locale-switcher';

export function PersonalSettings() {
  const t = useTranslations('personal');
  const { user } = useAuth();
  const [busy, setBusy] = useState(false);
  return (
    <div className="space-y-6 max-w-xl">
      <h1 className="text-2xl font-bold">{t('settings')}</h1>
      <div className="border rounded-xl p-5 space-y-3">
        <h2 className="font-semibold">{t('account')}</h2>
        <p>{user?.display_name}</p>
        <p className="text-muted-foreground">{user?.email}</p>
        <LocaleSwitcher />
      </div>
      <form
        className="border rounded-xl p-5 space-y-4"
        onSubmit={async (event) => {
          event.preventDefault();
          const fields = new FormData(event.currentTarget);
          if (fields.get('new') !== fields.get('repeat')) {
            toast.error(t('passwordMismatch'));
            return;
          }
          setBusy(true);
          try {
            await api.post('/auth/password', {
              current_password: fields.get('current'),
              new_password: fields.get('new'),
            });
            toast.success(t('passwordChanged'));
            await signOut({ callbackUrl: '/login' });
          } catch {
            toast.error(t('passwordChangeError'));
          } finally {
            setBusy(false);
          }
        }}
      >
        <h2 className="font-semibold">{t('changePassword')}</h2>
        <div className="space-y-1">
          <Label htmlFor="password-current">{t('currentPassword')}</Label>
          <Input
            id="password-current"
            name="current"
            type="password"
            autoComplete="current-password"
            required
            maxLength={128}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="password-new">{t('newPassword')}</Label>
          <Input
            id="password-new"
            name="new"
            type="password"
            autoComplete="new-password"
            required
            minLength={12}
            maxLength={128}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="password-repeat">{t('repeatPassword')}</Label>
          <Input
            id="password-repeat"
            name="repeat"
            type="password"
            autoComplete="new-password"
            required
            minLength={12}
            maxLength={128}
          />
        </div>
        <p className="text-sm text-muted-foreground">{t('passwordHelp')}</p>
        <Button disabled={busy}>{busy ? t('saving') : t('changePassword')}</Button>
      </form>
    </div>
  );
}
