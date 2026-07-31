import { useQuery } from '@tanstack/react-query';
import { getAiProviderOperations } from '../../../api/generated/sdk.gen';
import { contentServiceClient } from '../../../api/client/contentServiceClient';
import { unwrap } from '../../../api/client/adminApi';

type Row=Record<string,unknown>;
const value=(v:unknown)=>v==null||v===''?'—':String(v);
export function AiProvidersPage(){
  const query=useQuery({queryKey:['ai','provider-operations'],queryFn:()=>unwrap(getAiProviderOperations({client:contentServiceClient}))});
  const data=query.data;const attempts=(data?.recentAttempts??[]) as Row[];
  return <div className="page-stack"><header className="page-header"><div><p className="eyebrow">AI operations</p><h1>AI Providers</h1><p>Provider readiness, confirmed free capacity, routing decisions and recent attempts.</p></div><button className="secondary" onClick={()=>query.refetch()} disabled={query.isFetching}>Refresh provider status</button></header>
    <p className="warning" role="note"><strong>All providers operate under FREE_ONLY.</strong> When all free capacity is exhausted, generation pauses automatically. Paid fallback and automatic billing upgrades are disabled.</p>
    {query.isPending&&<p role="status">Loading provider status…</p>}{query.error&&<p role="alert" className="error">Provider status could not be loaded.</p>}
    {data&&<><section className="card"><h2>Routing policy</h2><div className="report-grid"><Metric label="Billing policy" value={data.billingPolicy}/><Metric label="Paid fallback" value={data.allowPaidFallback?'Enabled':'Disabled'}/><Metric label="Zero cost required" value={data.requireZeroCostProvider?'Yes':'No'}/><Metric label="Automatic billing upgrade" value={data.allowAutomaticBillingUpgrade?'Enabled':'Disabled'}/></div><p><strong>Priority:</strong> {data.priority.join(' → ')}</p></section>
    <section><h2>Providers</h2><div className="proposal-grid">{data.providers.map(p=><article className="card" key={p.provider}><div className="actions"><h3>{p.provider.replaceAll('_',' ')}</h3><span className="status-badge">{p.status.replaceAll('_',' ')}</span></div><dl><dt>Model</dt><dd>{value(p.model)}</dd><dt>Priority</dt><dd>{p.priority}</dd><dt>Enabled</dt><dd>{p.enabled?'Yes':'No'}</dd><dt>Credentials configured</dt><dd>{p.credentialConfigured?'Yes':'No'}</dd><dt>Confirmed free</dt><dd>{p.freeStatus}</dd><dt>Circuit</dt><dd>{value(p.circuitState)}</dd><dt>Next retry/reset</dt><dd>{p.nextRetryAt?new Date(p.nextRetryAt).toLocaleString():'—'}</dd></dl><details><summary>Capacity and capabilities</summary><pre>{JSON.stringify({capacity:p.capacity,capabilities:p.capabilities},null,2)}</pre></details></article>)}</div></section>
    <section className="card"><h2>Recent provider attempts</h2><div className="table-wrap"><table><thead><tr><th>Started</th><th>Operation</th><th>Provider</th><th>Model</th><th>Status</th><th>Free</th><th>Tokens</th><th>Latency</th><th>Error/fallback</th></tr></thead><tbody>{attempts.length===0?<tr><td colSpan={9}>No provider attempts recorded.</td></tr>:attempts.map((a,i)=><tr key={String(a.id??i)}><td>{a.startedAt?new Date(String(a.startedAt)).toLocaleString():'—'}</td><td>{value(a.operation)}</td><td>{value(a.provider)}</td><td>{value(a.model)}</td><td>{value(a.status)}</td><td>{a.confirmedFree===true?'Yes':'No'}</td><td>{Number(a.inputTokens??0)+Number(a.outputTokens??0)}</td><td>{a.latencyMs==null?'—':`${a.latencyMs} ms`}</td><td>{value(a.errorCode??a.fallbackReason)}</td></tr>)}</tbody></table></div></section></>}
  </div>;
}
function Metric({label,value}:{label:string,value:unknown}){return <div><small>{label}</small><strong>{String(value)}</strong></div>}
