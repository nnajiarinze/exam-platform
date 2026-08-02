import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';
import { environment } from '../config/environment';
import { isAdminRole, type AdminIdentity } from '../permissions/permissions';

export const userManager = new UserManager({
  authority: environment.oidcAuthority,
  client_id: environment.oidcClientId,
  redirect_uri: `${window.location.origin}/auth/callback`,
  post_logout_redirect_uri: `${window.location.origin}/login`,
  response_type: 'code', scope: environment.requiredScopes.join(' '),
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: sessionStorage, prefix: `oidc.${environment.appEnvironment}.` }),
});

export function clearEnvironmentOidcStorage(): void {
  for (let index = sessionStorage.length - 1; index >= 0; index -= 1) {
    const key = sessionStorage.key(index);
    if (key?.startsWith('oidc.LOCAL.') || key?.startsWith('oidc.HOSTED.')) sessionStorage.removeItem(key);
  }
}

export function identityFromUser(user: User): AdminIdentity {
  const access = (user.profile.realm_access as {roles?:string[]}|undefined) ?? accessTokenRealmAccess(user.access_token);
  const roles=(access?.roles??[]).filter(isAdminRole);
  return {id:user.profile.sub,displayName:String(user.profile.name??user.profile.preferred_username??'Administrator'),roles};
}

function accessTokenRealmAccess(token: string): { roles?: string[] } | undefined {
  try {
    const encodedPayload = token.split('.')[1];
    if (!encodedPayload) return undefined;
    const normalized = encodedPayload.replaceAll('-', '+').replaceAll('_', '/');
    const payload = JSON.parse(atob(normalized)) as { realm_access?: { roles?: string[] } };
    return payload.realm_access;
  } catch {
    return undefined;
  }
}
let currentUser:User|undefined;
export function setOidcUser(user?:User){currentUser=user;}
export function currentAccessToken(){return currentUser&&!currentUser.expired?currentUser.access_token:undefined;}
export function currentOidcSummary(){
  if(!currentUser||currentUser.expired)return null;
  return {subject:currentUser.profile.sub,username:String(currentUser.profile.preferred_username??currentUser.profile.name??currentUser.profile.sub),scopes:currentUser.scopes};
}
