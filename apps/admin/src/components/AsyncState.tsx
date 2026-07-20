import type { ReactNode } from 'react';
import { AdminIcon } from './AdminIcon';
export function AsyncState({loading,error,children}:{loading:boolean;error:unknown;children:ReactNode}){
  if(loading)return <div className="async-state" role="status" aria-live="polite"><span className="loading-spinner"/><strong>Loading…</strong></div>;
  if(error)return <div role="alert" className="async-state async-error"><AdminIcon name="warning"/><strong>Unable to load this content</strong><p>{error instanceof Error?error.message:'An unexpected error occurred.'}</p></div>;
  return <>{children}</>;
}
