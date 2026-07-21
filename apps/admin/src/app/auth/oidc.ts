import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';
import { environment } from '../config/environment';
import { isAdminRole, type AdminIdentity } from '../permissions/permissions';

export const userManager = new UserManager({
  authority: environment.oidcAuthority,
  client_id: environment.oidcClientId,
  redirect_uri: `${window.location.origin}/auth/callback`,
  post_logout_redirect_uri: `${window.location.origin}/login`,
  response_type: 'code', scope: 'openid profile email',
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: sessionStorage }),
});

export function identityFromUser(user: User): AdminIdentity {
  const access=user.profile.realm_access as {roles?:string[]}|undefined;
  const roles=(access?.roles??[]).filter(isAdminRole);
  return {id:user.profile.sub,displayName:String(user.profile.name??user.profile.preferred_username??'Administrator'),roles};
}
let currentUser:User|undefined;
export function setOidcUser(user?:User){currentUser=user;}
export function currentAccessToken(){return currentUser&&!currentUser.expired?currentUser.access_token:undefined;}
