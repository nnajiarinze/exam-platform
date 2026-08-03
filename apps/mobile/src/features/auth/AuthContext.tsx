import * as AuthSession from 'expo-auth-session';
import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';
import * as WebBrowser from 'expo-web-browser';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { appConfig } from '../../api/config';
import { useAppStore } from '../../app/store';
import { clearAccessToken, configureAuthTokens, updateAccessToken } from './authTokenStore';
import { AuthValidationError, validateAccessToken, validateIdToken, type JwtClaims } from './authSecurity';
import { cancelStudyReminder } from '../settings/reminders';

WebBrowser.maybeCompleteAuthSession();

const KEY_PREFIX = `svea-study.oidc.${appConfig.environment.toLowerCase()}`;
const REFRESH_KEY = `${KEY_PREFIX}.refresh-token`;
const SESSION_KEY = `${KEY_PREFIX}.session`;
// The discovery probe has its own short network deadline. This longer guard is
// only a failsafe for a system authentication session that never resolves; it
// must leave enough time for password-manager and MFA interaction.
const AUTH_TIMEOUT_MS = 10 * 60_000;

export type AuthStatus = 'restoring' | 'unauthenticated' | 'authenticating' | 'authenticated' | 'verification-required' | 'expired' | 'error';
export type AuthIntent = 'apple' | 'google' | 'email' | 'register' | 'password-reset' | 'change-password' | 'link-apple' | 'link-google' | 'reauthenticate';
export type AuthDiagnosticCode =
  | 'AUTH_CANCELLED' | 'AUTH_NETWORK_UNAVAILABLE' | 'AUTH_PROVIDER_UNAVAILABLE' | 'AUTH_CALLBACK_INVALID'
  | 'AUTH_TOKEN_EXCHANGE_FAILED' | 'AUTH_SESSION_EXPIRED' | 'AUTH_CONFIGURATION_INVALID';
type StoredSession = { environment: string; issuer: string; accessToken: string; idToken?: string; expiresAt: number; subject: string };
type Context = {
  status: AuthStatus;
  claims?: JwtClaims;
  activeIntent?: AuthIntent;
  diagnosticCode?: AuthDiagnosticCode;
  requestReady: boolean;
  appleEnabled: boolean;
  googleEnabled: boolean;
  login: (method?: 'apple' | 'google' | 'email') => Promise<void>;
  register: () => Promise<void>;
  forgotPassword: () => Promise<void>;
  changePassword: () => Promise<void>;
  linkProvider: (provider: 'apple' | 'google') => Promise<boolean>;
  reauthenticate: () => Promise<boolean>;
  logout: () => Promise<void>;
  clearError: () => void;
};
const AuthContext = createContext<Context | null>(null);

function oidcDiscovery(issuer: string): AuthSession.DiscoveryDocument {
  const base = issuer.replace(/\/+$/, '');
  return {
    authorizationEndpoint: `${base}/protocol/openid-connect/auth`,
    tokenEndpoint: `${base}/protocol/openid-connect/token`,
    revocationEndpoint: `${base}/protocol/openid-connect/revoke`,
    endSessionEndpoint: `${base}/protocol/openid-connect/logout`,
  };
}

async function identityAvailable(issuer: string): Promise<boolean> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8_000);
  try {
    return (await fetch(`${issuer.replace(/\/+$/, '')}/.well-known/openid-configuration`, { signal: controller.signal })).ok;
  } catch {
    return false;
  } finally {
    clearTimeout(timeout);
  }
}

function safeDiagnostic(error: unknown): AuthDiagnosticCode {
  if (error instanceof AuthValidationError) return 'AUTH_CALLBACK_INVALID';
  return 'AUTH_TOKEN_EXCHANGE_FAILED';
}

export function providerAuthorizationUrl(url: string, intent: AuthIntent): string {
  const parsed = new URL(url);
  if (intent === 'apple') parsed.searchParams.set('kc_idp_hint', appConfig.appleIdentityProvider);
  if (intent === 'google') parsed.searchParams.set('kc_idp_hint', appConfig.googleIdentityProvider);
  if (intent === 'link-apple') parsed.searchParams.set('kc_action', `idp_link:${appConfig.appleIdentityProvider}`);
  if (intent === 'link-google') parsed.searchParams.set('kc_action', `idp_link:${appConfig.googleIdentityProvider}`);
  if (intent === 'reauthenticate') { parsed.searchParams.set('prompt', 'login'); parsed.searchParams.set('max_age', '0'); }
  if (intent === 'password-reset' || intent === 'change-password') parsed.searchParams.set('kc_action', 'UPDATE_PASSWORD');
  if (intent === 'register') parsed.pathname = parsed.pathname.replace(/\/protocol\/openid-connect\/auth$/, '/protocol/openid-connect/registrations');
  return parsed.toString();
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const discovery = useMemo(() => oidcDiscovery(appConfig.oidcIssuer), []);
  const redirectUri = AuthSession.makeRedirectUri({ scheme: 'sveastudy', path: 'auth/callback' });
  const [nonce, setNonce] = useState<string>();
  const [request, response, promptAsync] = AuthSession.useAuthRequest({
    clientId: appConfig.oidcClientId,
    redirectUri,
    scopes: ['openid', 'profile', 'email', 'offline_access'],
    usePKCE: true,
    responseType: AuthSession.ResponseType.Code,
    extraParams: nonce ? { nonce, ui_locales: 'sv' } : {},
  }, nonce ? discovery : null);
  const [status, setStatus] = useState<AuthStatus>('restoring');
  const [currentClaims, setClaims] = useState<JwtClaims>();
  const [activeIntent, setActiveIntent] = useState<AuthIntent>();
  const [diagnosticCode, setDiagnosticCode] = useState<AuthDiagnosticCode>();
  const refreshInFlight = useRef<Promise<string | undefined> | undefined>(undefined);
  const authInFlight = useRef(false);
  const exchangedCode = useRef<string | undefined>(undefined);
  const expectedLinkedSubject = useRef<string | undefined>(undefined);
  const authCompletion = useRef<((completed: boolean) => void) | undefined>(undefined);
  const queryClient = useQueryClient();

  const rotateNonce = useCallback(() => { setNonce(Crypto.randomUUID().replaceAll('-', '')); }, []);
  useEffect(rotateNonce, [rotateNonce]);

  const clearStoredSession = useCallback(async () => {
    await Promise.all([SecureStore.deleteItemAsync(REFRESH_KEY), SecureStore.deleteItemAsync(SESSION_KEY)]);
  }, []);

  const setSession = useCallback(async (tokenResponse: AuthSession.TokenResponse, expectedNonce?: string, expectedSubject?: string) => {
    if (!tokenResponse.accessToken) throw new AuthValidationError('AUTH_TOKEN_MALFORMED');
    const accessClaims = validateAccessToken(tokenResponse.accessToken, appConfig.oidcIssuer, appConfig.oidcAudience);
    if (expectedSubject && accessClaims.sub !== expectedSubject) throw new AuthValidationError('AUTH_SUBJECT_CHANGED');
    if (expectedNonce) {
      if (!tokenResponse.idToken) throw new AuthValidationError('AUTH_ID_TOKEN_MISSING');
      validateIdToken(tokenResponse.idToken, appConfig.oidcIssuer, appConfig.oidcClientId, expectedNonce, accessClaims.sub!);
    }
    const expiresAt = (accessClaims.exp ?? 0) * 1000;
    if (tokenResponse.refreshToken) await SecureStore.setItemAsync(REFRESH_KEY, tokenResponse.refreshToken);
    await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify({
      environment: appConfig.environment, issuer: appConfig.oidcIssuer, accessToken: tokenResponse.accessToken,
      idToken: tokenResponse.idToken, expiresAt, subject: accessClaims.sub!,
    } satisfies StoredSession));
    setClaims(accessClaims);
    useAppStore.getState().setLearnerIdentity(accessClaims.sub!);
    updateAccessToken(tokenResponse.accessToken, expiresAt);
    setDiagnosticCode(undefined);
    setStatus(accessClaims.email_verified === false ? 'verification-required' : 'authenticated');
  }, []);

  const refresh = useCallback(async () => {
    if (refreshInFlight.current) return refreshInFlight.current;
    refreshInFlight.current = (async () => {
      try {
        const token = await SecureStore.getItemAsync(REFRESH_KEY);
        if (!token) return undefined;
        if (!await identityAvailable(appConfig.oidcIssuer)) {
          const stored = await SecureStore.getItemAsync(SESSION_KEY);
          if (stored) {
            const session = JSON.parse(stored) as StoredSession;
            if (session.environment === appConfig.environment && session.issuer === appConfig.oidcIssuer && session.expiresAt > Date.now()) {
              const value = validateAccessToken(session.accessToken, appConfig.oidcIssuer, appConfig.oidcAudience);
              setClaims(value); updateAccessToken(session.accessToken, session.expiresAt); setStatus('authenticated');
              return session.accessToken;
            }
          }
          setDiagnosticCode('AUTH_NETWORK_UNAVAILABLE'); setStatus('error'); return undefined;
        }
        try {
          const result = await AuthSession.refreshAsync({ clientId: appConfig.oidcClientId, refreshToken: token }, discovery);
          await setSession(result);
          return result.accessToken;
        } catch {
          await clearStoredSession(); clearAccessToken(); useAppStore.getState().clearUserData(); queryClient.clear();
          setDiagnosticCode('AUTH_SESSION_EXPIRED'); setStatus('expired'); return undefined;
        }
      } finally { refreshInFlight.current = undefined; }
    })();
    return refreshInFlight.current;
  }, [clearStoredSession, discovery, queryClient, setSession]);

  useEffect(() => {
    configureAuthTokens({ refresh, expired: () => { setDiagnosticCode('AUTH_SESSION_EXPIRED'); setStatus('expired'); } });
    void refresh().then(token => { if (!token && status === 'restoring') setStatus('unauthenticated'); });
  }, [refresh]);

  useEffect(() => {
    if (response?.type === 'success' && request?.codeVerifier && nonce && response.params.code && exchangedCode.current !== response.params.code) {
      exchangedCode.current = response.params.code;
      if (!currentClaims) setStatus('authenticating');
      void AuthSession.exchangeCodeAsync({ clientId: appConfig.oidcClientId, code: response.params.code, redirectUri, extraParams: { code_verifier: request.codeVerifier } }, discovery)
        .then(result => setSession(result, nonce, expectedLinkedSubject.current))
        .then(() => authCompletion.current?.(true))
        .catch(error => { setDiagnosticCode(safeDiagnostic(error)); setStatus('error'); authCompletion.current?.(false); })
        .finally(() => { authCompletion.current = undefined; authInFlight.current = false; expectedLinkedSubject.current = undefined; setActiveIntent(undefined); rotateNonce(); });
    } else if (response?.type === 'error') {
      authCompletion.current?.(false); authCompletion.current = undefined; authInFlight.current = false; setActiveIntent(undefined); setDiagnosticCode('AUTH_CALLBACK_INVALID'); setStatus('error'); rotateNonce();
    }
  }, [response, currentClaims, discovery, request, redirectUri, nonce, rotateNonce, setSession]);

  const start = useCallback(async (intent: AuthIntent) => {
    if (authInFlight.current) return false;
    if (((intent === 'apple' || intent === 'link-apple') && !appConfig.appleSignInEnabled) || ((intent === 'google' || intent === 'link-google') && !appConfig.googleSignInEnabled)) {
      setDiagnosticCode('AUTH_PROVIDER_UNAVAILABLE'); setStatus('error'); return false;
    }
    if (!request?.url || !nonce) {
      setDiagnosticCode('AUTH_CONFIGURATION_INVALID'); setStatus('error'); return false;
    }
    authInFlight.current = true;
    expectedLinkedSubject.current = intent.startsWith('link-') ? currentClaims?.sub : undefined;
    if (intent.startsWith('link-') && !expectedLinkedSubject.current) {
      authInFlight.current = false; setDiagnosticCode('AUTH_CONFIGURATION_INVALID'); setStatus('error'); return false;
    }
    setActiveIntent(intent); setDiagnosticCode(undefined); if (!currentClaims) setStatus('authenticating');
    if (!await identityAvailable(appConfig.oidcIssuer)) {
      authInFlight.current = false; setActiveIntent(undefined); setDiagnosticCode('AUTH_NETWORK_UNAVAILABLE'); setStatus('error'); return false;
    }
    const completion = new Promise<boolean>(resolve => { authCompletion.current = resolve; });
    const timeout = setTimeout(() => { authCompletion.current?.(false); authCompletion.current = undefined; authInFlight.current = false; expectedLinkedSubject.current = undefined; setActiveIntent(undefined); setDiagnosticCode('AUTH_NETWORK_UNAVAILABLE'); setStatus('error'); }, AUTH_TIMEOUT_MS);
    try {
      const result = await promptAsync({ url: providerAuthorizationUrl(request.url, intent), preferEphemeralSession: false });
      if (result.type === 'cancel' || result.type === 'dismiss' || result.type === 'locked') {
        clearTimeout(timeout);
        authCompletion.current?.(false); authCompletion.current = undefined; authInFlight.current = false; expectedLinkedSubject.current = undefined; setActiveIntent(undefined); setDiagnosticCode(result.type === 'locked' ? 'AUTH_PROVIDER_UNAVAILABLE' : undefined); setStatus(currentClaims ? 'authenticated' : 'unauthenticated'); rotateNonce();
        return false;
      }
    } catch {
      clearTimeout(timeout);
      authCompletion.current?.(false); authCompletion.current = undefined; authInFlight.current = false; expectedLinkedSubject.current = undefined; setActiveIntent(undefined); setDiagnosticCode('AUTH_PROVIDER_UNAVAILABLE'); setStatus('error'); rotateNonce(); return false;
    }
    const completed = await completion;
    clearTimeout(timeout);
    return completed;
  }, [currentClaims?.sub, nonce, promptAsync, request?.url, rotateNonce]);

  const logout = useCallback(async () => {
    const refreshToken = await SecureStore.getItemAsync(REFRESH_KEY);
    if (currentClaims?.sub) await cancelStudyReminder(currentClaims.sub);
    if (refreshToken) try { await AuthSession.revokeAsync({ token: refreshToken, clientId: appConfig.oidcClientId }, discovery); } catch { /* Local cleanup remains authoritative. */ }
    await clearStoredSession(); clearAccessToken(); setClaims(undefined); useAppStore.getState().clearUserData(); queryClient.clear();
    setDiagnosticCode(undefined); setStatus('unauthenticated');
  }, [clearStoredSession, currentClaims?.sub, discovery, queryClient]);

  const value = useMemo<Context>(() => ({
    status, claims: currentClaims, activeIntent, diagnosticCode, requestReady: Boolean(request?.url && nonce),
    appleEnabled: appConfig.appleSignInEnabled, googleEnabled: appConfig.googleSignInEnabled,
    login: async method => { await start(method ?? 'email'); }, register: async () => { await start('register'); }, forgotPassword: async () => { await start('password-reset'); },
    changePassword: async () => { await start('change-password'); }, linkProvider: provider => start(provider === 'apple' ? 'link-apple' : 'link-google'),
    reauthenticate: () => start('reauthenticate'), logout,
    clearError: () => { setDiagnosticCode(undefined); setStatus('unauthenticated'); },
  }), [activeIntent, currentClaims, diagnosticCode, logout, nonce, request?.url, start, status]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used within AuthProvider');
  return value;
}
