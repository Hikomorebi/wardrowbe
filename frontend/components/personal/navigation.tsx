'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { Shirt, LayoutGrid, MapPin, Settings } from 'lucide-react';

export function PersonalNavigation() {
  const t = useTranslations('personal');
  const pathname = usePathname();
  const links = [
    { href: '/dashboard/personal/wardrobe', name: t('wardrobe'), icon: Shirt },
    { href: '/dashboard/personal/outfits', name: t('outfits'), icon: LayoutGrid },
    { href: '/dashboard/personal/locations', name: t('manageLocations'), icon: MapPin },
    { href: '/dashboard/personal/settings', name: t('settings'), icon: Settings },
  ];
  return (
    <nav className="fixed bottom-0 inset-x-0 z-50 border-t bg-background flex justify-around lg:inset-y-0 lg:right-auto lg:w-72 lg:border-r lg:border-t-0 lg:p-6 lg:flex-col lg:justify-start lg:gap-3">
      <Link href="/dashboard/personal/wardrobe" className="hidden lg:block text-xl font-bold mb-8">
        {t('brand')}
      </Link>
      {links.map((link) => (
        <Link
          key={link.href}
          href={link.href}
          className={`flex items-center flex-col lg:flex-row gap-2 p-3 rounded-lg text-sm ${pathname === link.href ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'}`}
        >
          <link.icon className="h-5 w-5" />
          <span>{link.name}</span>
        </Link>
      ))}
    </nav>
  );
}
