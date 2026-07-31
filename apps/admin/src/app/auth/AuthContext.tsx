import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { environment, type AppEnvironment } from '../config/environment';
import { isAdminRole, type AdminIdentity } from '../permissions/permissions';
import { clearDevelopmentAdmin, readDevelopmentAdmin, storeDevelopmentAdmin } from './authSession';
import { identityFromUser, setOidcUser, userManager } from './oidc';
import { useQueryClient } from '@tanstack/react-query';
import { switchAdminEnvironment } from './environmentSwitch';

export type DevelopmentProfile = 'administrator' | 'reviewer';
interface AuthState { admin: AdminIdentity | null; loading:boolean; signIn: (profile: DevelopmentProfile) => void; login:()=>Promise<void>; completeCallback:()=>Promise<void>; signOut: () => void; switchEnvironment:(next:AppEnvironment)=>Promise<void> }
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<AdminIdentity | null>(readDevelopmentAdmin);
  const [loading,setLoading]=useState(!environment.developmentAuthEnabled);const queryClient=useQueryClient();
  useEffect(()=>{if(environment.developmentAuthEnabled)return;void userManager.getUser().then(user=>{if(user&&!user.expired){setOidcUser(user);setAdmin(identityFromUser(user));}setLoading(false)});const expired=()=>{setOidcUser();setAdmin(null);queryClient.clear()};userManager.events.addAccessTokenExpired(expired);return()=>userManager.events.removeAccessTokenExpired(expired);},[queryClient]);
  const value = useMemo<AuthState>(() => ({
    admin,
    loading,
    signIn: (profile) => {
      if (!environment.developmentAuthEnabled) return;
      const identity: AdminIdentity = profile === 'reviewer'
        ? { id: environment.developmentReviewerId!, displayName: environment.developmentReviewerName!, roles: environment.developmentReviewerRoles.filter(isAdminRole) }
        : { id: environment.developmentAdminId!, displayName: environment.developmentAdminName!, roles: environment.developmentAdminRoles.filter(isAdminRole) };
      storeDevelopmentAdmin(identity); setAdmin(identity);
    },
    login:()=>userManager.signinRedirect({state:{returnTo:window.location.pathname}}),
    completeCallback:async()=>{const user=await userManager.signinRedirectCallback();setOidcUser(user);setAdmin(identityFromUser(user));setLoading(false);},
    signOut: () => {
      clearDevelopmentAdmin(); setOidcUser(); setAdmin(null); queryClient.clear();
      if (!environment.developmentAuthEnabled) {
        void userManager.removeUser().finally(() => window.location.assign('/login'));
      }
    },
    switchEnvironment: async(next) => {
      if (!environment.environmentSwitcherEnabled || next === environment.appEnvironment) return;
      await switchAdminEnvironment(next, queryClient);
    },
  }), [admin,loading,queryClient]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
// The hook intentionally shares this module with its provider so they cannot drift.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState { const context = useContext(AuthContext); if (!context) throw new Error('useAuth must be used inside AuthProvider'); return context; }
