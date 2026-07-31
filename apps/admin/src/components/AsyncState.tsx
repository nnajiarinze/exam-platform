import type { ReactNode } from 'react';
import { AdminIcon } from './AdminIcon';
import { ApiError } from '../api/errors/ApiError';

const titles = {
  AUTHENTICATION: 'Sign-in required', FORBIDDEN: 'Access denied', NETWORK: 'Backend unavailable',
  TIMEOUT: 'Request timed out', CONFIGURATION: 'Environment misconfigured', SERVER: 'Server error',
} as const;
export function AsyncState({loading,error,children}:{loading:boolean;error:unknown;children:ReactNode}){
  if(loading)return <div className="async-state" role="status" aria-live="polite"><span className="loading-spinner"/><strong>Loading…</strong></div>;
  if(error){const apiError=error instanceof ApiError?error:undefined;const title=apiError&&apiError.kind in titles?titles[apiError.kind as keyof typeof titles]:'Unable to load this content';return <div role="alert" className="async-state async-error"><AdminIcon name="warning"/><strong>{title}</strong><p>{error instanceof Error?error.message:'An unexpected error occurred.'}</p>{apiError?.correlationId&&<small>Request ID: {apiError.correlationId}</small>}<div className="button-row"><button type="button" onClick={()=>window.location.reload()}>Retry</button>{(apiError?.kind==='AUTHENTICATION'||apiError?.kind==='FORBIDDEN')&&<a className="button secondary" href="/login">Re-authenticate</a>}</div></div>};
  return <>{children}</>;
}
