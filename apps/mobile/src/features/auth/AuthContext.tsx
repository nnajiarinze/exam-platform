import * as AuthSession from 'expo-auth-session';
import * as SecureStore from 'expo-secure-store';
import * as WebBrowser from 'expo-web-browser';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { appConfig } from '../../api/config';
import { useAppStore } from '../../app/store';
import { clearAccessToken, configureAuthTokens, updateAccessToken } from './authTokenStore';
import { cancelStudyReminder } from '../settings/reminders';

WebBrowser.maybeCompleteAuthSession();
const REFRESH_KEY = 'svea-study.oidc.refresh-token';
type AuthStatus = 'restoring' | 'unauthenticated' | 'authenticated' | 'verification-required' | 'expired';
type Claims = { sub: string; email?: string; email_verified?: boolean; name?: string; exp?: number };
type Context = { status: AuthStatus; claims?: Claims; login: () => Promise<void>; register: () => Promise<void>; forgotPassword: () => Promise<void>; changePassword: () => Promise<void>; logout: () => Promise<void> };
const AuthContext = createContext<Context | null>(null);

function claims(token: string): Claims { const value=token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/'); return JSON.parse(globalThis.atob(value)) as Claims; }

export function AuthProvider({ children }: { children: ReactNode }) {
  const discovery = AuthSession.useAutoDiscovery(appConfig.oidcIssuer);
  const redirectUri = AuthSession.makeRedirectUri({ scheme: 'sveastudy', path: 'auth/callback' });
  const [request, response, promptAsync] = AuthSession.useAuthRequest({ clientId: appConfig.oidcClientId, redirectUri, scopes: ['openid','profile','email','offline_access'], usePKCE: true }, discovery);
  const [status,setStatus]=useState<AuthStatus>('restoring'); const[currentClaims,setClaims]=useState<Claims|undefined>(undefined); const refreshInFlight=useRef<Promise<string|undefined>|undefined>(undefined); const queryClient=useQueryClient();
  const setSession=useCallback(async(tokenResponse:AuthSession.TokenResponse)=>{if(!tokenResponse.accessToken)return;const parsed=claims(tokenResponse.accessToken);if(tokenResponse.refreshToken)await SecureStore.setItemAsync(REFRESH_KEY,tokenResponse.refreshToken);setClaims(parsed);useAppStore.getState().setLearnerIdentity(parsed.sub);updateAccessToken(tokenResponse.accessToken,(parsed.exp??0)*1000);setStatus(parsed.email_verified===false?'verification-required':'authenticated');},[]);
  const refresh=useCallback(async()=>{if(!discovery)return undefined;if(refreshInFlight.current)return refreshInFlight.current;refreshInFlight.current=(async()=>{const token=await SecureStore.getItemAsync(REFRESH_KEY);if(!token)return undefined;try{const result=await AuthSession.refreshAsync({clientId:appConfig.oidcClientId,refreshToken:token},discovery);await setSession(result);return result.accessToken;}catch{await SecureStore.deleteItemAsync(REFRESH_KEY);clearAccessToken();useAppStore.getState().clearUserData();queryClient.clear();setStatus('expired');return undefined;}finally{refreshInFlight.current=undefined;}})();return refreshInFlight.current;},[discovery,queryClient,setSession]);
  useEffect(()=>{configureAuthTokens({refresh,expired:()=>setStatus('expired')});void refresh().then(token=>{if(!token)setStatus('unauthenticated')});},[refresh]);
  useEffect(()=>{if(response?.type==='success'&&discovery&&request?.codeVerifier){void AuthSession.exchangeCodeAsync({clientId:appConfig.oidcClientId,code:response.params.code,redirectUri,extraParams:{code_verifier:request.codeVerifier}},discovery).then(setSession);}else if(response?.type==='error')setStatus('unauthenticated');},[response,discovery,request,redirectUri,setSession]);
  const start=useCallback(async(action?:string)=>{if(!request)return;setStatus('restoring');let actionUrl:string|undefined;if(action==='REGISTER'&&request.url)actionUrl=request.url.replace('/protocol/openid-connect/auth?','/protocol/openid-connect/registrations?');else if(action&&request.url)actionUrl=`${request.url}&kc_action=${encodeURIComponent(action)}`;await promptAsync(actionUrl?{url:actionUrl}:{});},[request,promptAsync]);
  const logout=useCallback(async()=>{const refreshToken=await SecureStore.getItemAsync(REFRESH_KEY);if(currentClaims?.sub)await cancelStudyReminder(currentClaims.sub);if(refreshToken&&discovery)try{await AuthSession.revokeAsync({token:refreshToken,clientId:appConfig.oidcClientId},discovery);}catch{/* Local credentials are still cleared. */}await SecureStore.deleteItemAsync(REFRESH_KEY);clearAccessToken();setClaims(undefined);useAppStore.getState().clearUserData();queryClient.clear();setStatus('unauthenticated');},[currentClaims?.sub,discovery,queryClient]);
  const value=useMemo<Context>(()=>({status,claims:currentClaims,login:()=>start(),register:()=>start('REGISTER'),forgotPassword:()=>start('UPDATE_PASSWORD'),changePassword:()=>start('UPDATE_PASSWORD'),logout}),[status,currentClaims,start,logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth(){const value=useContext(AuthContext);if(!value)throw new Error('useAuth must be used within AuthProvider');return value;}
