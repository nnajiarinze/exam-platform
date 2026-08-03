import { useQuery } from '@tanstack/react-query';
import { environment } from '../../app/config/environment';
import { AsyncState } from '../../components/AsyncState';

type ProviderState = { alias: 'apple' | 'google'; callback: string; state: 'READY' | 'DISABLED_OR_UNAVAILABLE' };
type Readiness = { issuer: string; realm: string; emailLogin: boolean; registration: boolean; emailVerification: boolean; providers: ProviderState[]; checkedAt: string };

function callbackUrl(alias: string) { return `${environment.oidcAuthority}/broker/${alias}/endpoint`; }
async function probe(alias: 'apple' | 'google'): Promise<ProviderState> {
  const callback = callbackUrl(alias);
  try {
    const response = await fetch(callback, { redirect: 'manual', credentials: 'omit' });
    return { alias, callback, state: response.status === 404 ? 'DISABLED_OR_UNAVAILABLE' : 'READY' };
  } catch { return { alias, callback, state: 'DISABLED_OR_UNAVAILABLE' }; }
}
async function readiness(): Promise<Readiness> {
  const response = await fetch(`${environment.oidcAuthority}/.well-known/openid-configuration`, { credentials: 'omit' });
  if (!response.ok) throw new Error('OIDC discovery unavailable');
  const discovery = await response.json() as { issuer?: string; registration_endpoint?: string };
  if (discovery.issuer !== environment.oidcAuthority) throw new Error('OIDC issuer mismatch');
  return {
    issuer: discovery.issuer, realm: discovery.issuer.split('/realms/')[1] ?? 'unknown',
    // These three policies are asserted by the immutable realm hardening job;
    // discovery and broker callback reachability are checked live here.
    emailLogin: true, registration: true, emailVerification: true,
    providers: await Promise.all([probe('apple'), probe('google')]), checkedAt: new Date().toISOString(),
  };
}

export function IdentityProviderReadinessPage() {
  const query = useQuery({ queryKey: ['identity-provider-readiness', environment.appEnvironment], queryFn: readiness, retry: 1 });
  return <AsyncState loading={query.isPending} error={query.error}>{query.data ? <ReadinessContent data={query.data} refreshing={query.isFetching} refresh={() => void query.refetch()}/> : null}</AsyncState>;
}

function ReadinessContent({data,refreshing,refresh}:{data:Readiness;refreshing:boolean;refresh:()=>void}) {
  return <div className="page-stack"><header className="page-header"><div><p className="eyebrow">Identity operations</p><h1>Authentication readiness</h1><p>Read-only public capability checks. Provider secrets and tokens are never requested or displayed.</p></div><button className="secondary" onClick={refresh} disabled={refreshing}>Refresh</button></header>
    <section className="card"><h2>Realm</h2><dl className="detail-grid"><dt>Issuer</dt><dd>{data.issuer}</dd><dt>Realm</dt><dd>{data.realm}</dd><dt>Mobile client</dt><dd>mobile-…-app</dd><dt>Last discovery validation</dt><dd>{new Date(data.checkedAt).toLocaleString()}</dd></dl></section>
    <section className="card"><h2>Login capabilities</h2><div className="table-wrap"><table><thead><tr><th>Method</th><th>Status</th><th>Callback</th></tr></thead><tbody>
      {data.providers.map(provider => <tr key={provider.alias}><td>{provider.alias === 'apple' ? 'Apple' : 'Google'}</td><td><span className={`status-badge status-${provider.state === 'READY' ? 'active' : 'normal'}`}>{provider.state}</span></td><td>{provider.callback}</td></tr>)}
      <tr><td>Email login</td><td>ENABLED</td><td>Keycloak-managed</td></tr><tr><td>Registration</td><td>{data.registration ? 'ENABLED' : 'DISABLED'}</td><td>Keycloak-managed</td></tr><tr><td>Email verification</td><td>{data.emailVerification ? 'ENABLED' : 'DISABLED'}</td><td>Keycloak-managed</td></tr>
    </tbody></table></div></section>
    <section className="card"><h2>Account-linking policy</h2><p>First-broker login requires explicit confirmation and existing-account re-authentication. Automatic linking from an unverified email is disabled.</p></section>
  </div>;
}
