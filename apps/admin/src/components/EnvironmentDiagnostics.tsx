import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { environment } from '../app/config/environment';
import { currentOidcSummary } from '../app/auth/oidc';
import { useAuth } from '../app/auth/AuthContext';
import { adminQueryKey } from '../api/query-keys/adminQueryKeys';
import { contentServiceClient } from '../api/client/contentServiceClient';
import { unwrap } from '../api/client/adminApi';
import { getAiProviderOperations,getContentServiceStatus,getLearnerHealthReport,listReleases } from '../api/generated/sdk.gen';

const state=(query:{isPending:boolean;error:unknown})=>query.isPending?'CHECKING':query.error?'ERROR':'READY';
export function EnvironmentDiagnostics(){
  const [open,setOpen]=useState(false);
  const {admin}=useAuth();const oidc=currentOidcSummary();
  const content=useQuery({queryKey:adminQueryKey('diagnostics','content'),queryFn:()=>unwrap(getContentServiceStatus({client:contentServiceClient})),enabled:open});
  const ai=useQuery({queryKey:adminQueryKey('diagnostics','ai'),queryFn:()=>unwrap(getAiProviderOperations({client:contentServiceClient})),enabled:open});
  const learning=useQuery({queryKey:adminQueryKey('diagnostics','learning'),queryFn:()=>unwrap(getLearnerHealthReport({client:contentServiceClient})),enabled:open});
  const releases=useQuery({queryKey:adminQueryKey('diagnostics','release'),queryFn:()=>unwrap(listReleases({client:contentServiceClient,query:{page:0,size:20,status:'ACTIVE'}})),enabled:open});
  const active=releases.data?.items?.[0];
  return <details className="environment-diagnostics" open={open} onToggle={event=>setOpen(event.currentTarget.open)}><summary>Environment diagnostics</summary>{open&&<><div className="diagnostics-grid">
    <Item label="Selected environment" value={environment.appEnvironment}/><Item label="Content API" value={environment.contentServiceBaseUrl}/><Item label="AI API" value={environment.aiServiceBaseUrl}/><Item label="Learning API" value={environment.learningServiceBaseUrl}/><Item label="OIDC issuer" value={environment.oidcAuthority}/><Item label="OIDC client" value={environment.oidcClientId}/><Item label="Authenticated principal" value={oidc?.username??admin?.displayName}/><Item label="Subject" value={oidc?.subject??admin?.id}/><Item label="Roles" value={admin?.roles.join(', ')}/><Item label="Scopes" value={(oidc?.scopes??environment.requiredScopes).join(' ')}/><Item label="Content readiness / last request" value={state(content)}/><Item label="AI readiness / last request" value={state(ai)}/><Item label="Learning readiness / last request" value={state(learning)}/><Item label="Active hosted release" value={active?.releaseNumber}/><Item label="Release checksum" value={active?.checksum}/>
  </div>{[content.error,ai.error,learning.error,releases.error].some(Boolean)&&<p role="alert" className="error">One or more administrative services failed. Open the affected screen for the full error; no empty result was substituted.</p>}</>}</details>;
}
function Item({label,value}:{label:string;value:unknown}){return <div><small>{label}</small><strong>{value==null||value===''?'—':String(value)}</strong></div>}
