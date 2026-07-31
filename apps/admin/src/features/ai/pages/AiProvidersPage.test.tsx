import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import { render,screen } from '@testing-library/react';
import { vi } from 'vitest';
import { AiProvidersPage } from './AiProvidersPage';

const operations=vi.hoisted(()=>({billingPolicy:'FREE_ONLY' as const,allowPaidFallback:false,requireZeroCostProvider:true,allowAutomaticBillingUpgrade:false,priority:['GEMINI','GROQ','CLOUDFLARE_WORKERS_AI','OPENROUTER_FREE'],providers:[{provider:'GEMINI',model:'gemini-3.1-flash-lite',enabled:true,credentialConfigured:true,billingPolicy:'FREE_ONLY',confirmedFree:true,freeStatus:'KNOWN',status:'READY',circuitState:'CLOSED',capacity:{requestsRemaining:10},capabilities:{structuredJson:true},priority:1},{provider:'GROQ',model:'',enabled:false,credentialConfigured:false,billingPolicy:'FREE_ONLY',confirmedFree:false,freeStatus:'UNKNOWN',status:'DISABLED',circuitState:'UNKNOWN',capacity:{},capabilities:{},priority:2}],recentAttempts:[],recentRouting:[]}));
vi.mock('../../../api/generated/sdk.gen',()=>({getAiProviderOperations:vi.fn().mockResolvedValue({data:operations})}));

test('shows FREE_ONLY routing without exposing credentials',async()=>{
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});render(<QueryClientProvider client={client}><AiProvidersPage/></QueryClientProvider>);
  expect(screen.getByRole('heading',{name:'AI Providers'})).toBeInTheDocument();expect(screen.getByText(/all providers operate under free_only/i)).toBeInTheDocument();expect(await screen.findByText('GEMINI')).toBeInTheDocument();expect(screen.getByText('GROQ')).toBeInTheDocument();expect(document.body.textContent).not.toMatch(/api.?key|authorization|secret/i);
});
