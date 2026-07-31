import type { QueryClient } from '@tanstack/react-query';
import { persistEnvironment, type AppEnvironment } from '../config/environment';
import { clearDevelopmentAdmin } from './authSession';
import { clearEnvironmentOidcStorage, setOidcUser, userManager } from './oidc';

export async function switchAdminEnvironment(
  next: AppEnvironment,
  queryClient: QueryClient,
  reload: () => void = () => window.location.reload(),
): Promise<void> {
  await userManager.removeUser();
  clearEnvironmentOidcStorage();
  clearDevelopmentAdmin();
  setOidcUser();
  queryClient.clear();
  persistEnvironment(next);
  reload();
}
