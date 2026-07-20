import type { ReactNode } from 'react';
export function AsyncState({loading,error,children}:{loading:boolean;error:unknown;children:ReactNode}){if(loading)return <p role="status">Loading…</p>;if(error)return <p role="alert" className="error">{error instanceof Error?error.message:'An unexpected error occurred.'}</p>;return <>{children}</>;}
