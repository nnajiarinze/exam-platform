import { useQuery } from '@tanstack/react-query';
import { environment } from '../../app/config/environment';
import { AsyncState } from '../../components/AsyncState';

type ProviderState = { alias: 'apple' | 'google'; enabled: boolean; callback: string; extensionVersion?: string | null; clientSecretExpiry?: string | null };
type EmailState = { enabled: boolean; verificationRequired: boolean; smtpConfigured: boolean; provider: string; sender: string; replyTo: string; domain: string; domainStatus: string; spfStatus: string; dkimStatus: string; dmarcStatus: string; lastSmtpTestAt: string | null; lastVerificationEmailAt: string | null; lastResetEmailAt: string | null; passwordResetEnabled: boolean };
type Readiness = { issuer: string; mobileCallback: string; bffReady: boolean; accountDeletionEnabled: boolean; email: EmailState; providers: ProviderState[]; providerErrorCount24h: number; accountLinkingConflictCount24h: number; checkedAt: string };

async function readiness(): Promise<Readiness> {
  const response = await fetch(`${environment.learningServiceBaseUrl}/api/v1/auth/readiness`, { credentials: 'omit' });
  if (!response.ok) throw new Error('Authentication readiness unavailable');
  return response.json() as Promise<Readiness>;
}

export function IdentityProviderReadinessPage() {
  const query = useQuery({ queryKey: ['identity-provider-readiness', environment.appEnvironment], queryFn: readiness, retry: 1 });
  return <AsyncState loading={query.isPending} error={query.error}>{query.data ? <ReadinessContent data={query.data} refreshing={query.isFetching} refresh={() => void query.refetch()}/> : null}</AsyncState>;
}

function ReadinessContent({data,refreshing,refresh}:{data:Readiness;refreshing:boolean;refresh:()=>void}) {
  return <div className="page-stack"><header className="page-header"><div><p className="eyebrow">Identity operations</p><h1>Authentication readiness</h1><p>Server-derived capability and audit health. Provider secrets and tokens are never requested or displayed.</p></div><button className="secondary" onClick={refresh} disabled={refreshing}>Refresh</button></header>
    <section className="card"><h2>Realm and mobile client</h2><dl className="detail-grid"><dt>Issuer</dt><dd>{data.issuer}</dd><dt>Mobile callback</dt><dd>{data.mobileCallback}</dd><dt>Identity BFF</dt><dd>{data.bffReady ? 'READY' : 'NOT CONFIGURED'}</dd><dt>Account deletion</dt><dd>{data.accountDeletionEnabled ? 'ENABLED' : 'DISABLED'}</dd><dt>Last validation</dt><dd>{new Date(data.checkedAt).toLocaleString()}</dd></dl></section>
    <section className="card"><h2>Login capabilities</h2><div className="table-wrap"><table><thead><tr><th>Method</th><th>Status</th><th>Callback / details</th></tr></thead><tbody>
      {data.providers.map(provider => <tr key={provider.alias}><td>{provider.alias === 'apple' ? 'Apple' : 'Google'}</td><td><span className={`status-badge status-${provider.enabled ? 'active' : 'normal'}`}>{provider.enabled ? 'READY' : 'DISABLED'}</span></td><td>{provider.callback}{provider.extensionVersion ? ` · extension ${provider.extensionVersion}` : ''}{provider.clientSecretExpiry ? ` · secret ${provider.clientSecretExpiry}` : ''}</td></tr>)}
      <tr><td>Email login</td><td>{data.email.enabled ? 'ENABLED' : 'DISABLED'}</td><td>Keycloak-managed</td></tr><tr><td>Registration</td><td>{data.email.enabled ? 'ENABLED' : 'DISABLED'}</td><td>Keycloak-managed</td></tr><tr><td>Email verification</td><td>{data.email.verificationRequired ? 'REQUIRED' : 'NOT REQUIRED'}</td><td>SMTP {data.email.smtpConfigured ? 'READY' : 'NOT CONFIGURED'}</td></tr>
      <tr><td>Email provider</td><td>{data.email.provider}</td><td>{data.email.sender || 'Sender unavailable'}</td></tr>
      <tr><td>Reply-to</td><td>{data.email.replyTo || 'Unavailable'}</td><td>Password reset {data.email.passwordResetEnabled ? 'ENABLED' : 'DISABLED'}</td></tr>
      <tr><td>Sending domain</td><td>{data.email.domain}</td><td>Domain {data.email.domainStatus}; SPF {data.email.spfStatus}; DKIM {data.email.dkimStatus}; DMARC {data.email.dmarcStatus}</td></tr>
      <tr><td>Last SMTP test</td><td>{data.email.lastSmtpTestAt || 'Not recorded'}</td><td>Verification {data.email.lastVerificationEmailAt || 'Not recorded'}; reset {data.email.lastResetEmailAt || 'Not recorded'}</td></tr>
    </tbody></table></div></section>
    <section className="card"><h2>Account safety</h2><p>First-broker login requires explicit confirmation and existing-account re-authentication. Automatic linking is disabled. The final usable login method cannot be removed.</p><dl className="detail-grid"><dt>Provider errors (24h)</dt><dd>{data.providerErrorCount24h}</dd><dt>Linking conflicts (24h)</dt><dd>{data.accountLinkingConflictCount24h}</dd></dl></section>
  </div>;
}
