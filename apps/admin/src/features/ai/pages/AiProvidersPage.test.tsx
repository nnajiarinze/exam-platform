import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import { render,screen } from '@testing-library/react';
import { vi } from 'vitest';
import { AiProvidersPage } from './AiProvidersPage';

const operations=vi.hoisted(()=>({billingPolicy:'FREE_FIRST_CAPPED_PAID' as const,allowPaidFallback:true,requireZeroCostProvider:true as const,allowAutomaticBillingUpgrade:false as const,priority:['GEMINI','GROQ','OPENROUTER_FREE','OPENROUTER_PAID'],providers:[{provider:'GEMINI',model:'gemini-3.1-flash-lite',enabled:true,credentialConfigured:true,billingPolicy:'FREE_ONLY' as const,confirmedFree:true,freeStatus:'KNOWN' as const,status:'READY',circuitState:'CLOSED',capacity:{requestsRemaining:10},capabilities:{structuredJson:true},priority:1},{provider:'GROQ',model:'',enabled:false,credentialConfigured:false,billingPolicy:'FREE_ONLY' as const,confirmedFree:false,freeStatus:'UNKNOWN' as const,status:'DISABLED',circuitState:'UNKNOWN',capacity:{},capabilities:{},priority:2},{provider:'OPENROUTER_PAID',model:'openai/gpt-oss-120b',enabled:true,credentialConfigured:true,billingPolicy:'CAPPED_PAID' as const,confirmedFree:false,freeStatus:'UNKNOWN' as const,status:'READY',circuitState:'CLOSED',capacity:{configuredBudgetUsd:14,spentUsd:0,remainingUsd:14,estimatedNextRequestUsd:0.006},capabilities:{structuredJson:true},priority:4}],currentRoutingDecision:'No routing decision recorded',recentAttempts:[],recentRouting:[],paidAccounting:[]}));
vi.mock('../../../api/generated/sdk.gen',()=>({getAiProviderOperations:vi.fn().mockResolvedValue({data:operations})}));

test('shows free-first capped routing and budget without exposing credentials',async()=>{
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});render(<QueryClientProvider client={client}><AiProvidersPage/></QueryClientProvider>);
  expect(screen.getByRole('heading',{name:'AI Providers'})).toBeInTheDocument();expect(screen.getByText(/free providers always run first/i)).toBeInTheDocument();expect(await screen.findByText('GEMINI')).toBeInTheDocument();expect(screen.getByText('GROQ')).toBeInTheDocument();expect(screen.getByText('OPENROUTER PAID')).toBeInTheDocument();expect(screen.getAllByText('$14.000000')).toHaveLength(2);expect(document.body.textContent).not.toMatch(/api.?key|authorization|secret/i);
});
