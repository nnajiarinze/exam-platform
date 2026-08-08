type Provider = 'google' | 'apple';
type LinkInitiation = { keycloakAction: string };

export async function linkIdentityProvider<T>({
  provider,
  initiate,
  link,
  reload,
}: {
  provider: Provider;
  initiate: () => Promise<LinkInitiation>;
  link: () => Promise<boolean>;
  reload: () => Promise<T>;
}): Promise<T | undefined> {
  const initiation = await initiate();
  if (initiation.keycloakAction !== `idp_link:${provider}`) throw new Error('Invalid identity-link action');
  if (!await link()) return undefined;
  return reload();
}
