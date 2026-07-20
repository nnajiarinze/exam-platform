import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { environment } from '../config/environment';
import { isAdminRole, type AdminIdentity } from '../permissions/permissions';
import { clearDevelopmentAdmin, readDevelopmentAdmin, storeDevelopmentAdmin } from './authSession';

export type DevelopmentProfile = 'administrator' | 'reviewer';
interface AuthState { admin: AdminIdentity | null; signIn: (profile: DevelopmentProfile) => void; signOut: () => void }
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<AdminIdentity | null>(readDevelopmentAdmin);
  const value = useMemo<AuthState>(() => ({
    admin,
    signIn: (profile) => {
      if (!environment.developmentAuthEnabled) return;
      const identity: AdminIdentity = profile === 'reviewer'
        ? { id: environment.developmentReviewerId!, displayName: environment.developmentReviewerName!, roles: environment.developmentReviewerRoles.filter(isAdminRole) }
        : { id: environment.developmentAdminId!, displayName: environment.developmentAdminName!, roles: environment.developmentAdminRoles.filter(isAdminRole) };
      storeDevelopmentAdmin(identity); setAdmin(identity);
    },
    signOut: () => { clearDevelopmentAdmin(); setAdmin(null); },
  }), [admin]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
// The hook intentionally shares this module with its provider so they cannot drift.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState { const context = useContext(AuthContext); if (!context) throw new Error('useAuth must be used inside AuthProvider'); return context; }
