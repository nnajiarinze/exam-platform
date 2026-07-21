import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportsPage } from './ReportsPage';

const api=vi.hoisted(()=>({status:vi.fn(),alerts:vi.fn(),disable:vi.fn(),recheck:vi.fn(),ack:vi.fn(),report:vi.fn()}));
vi.mock('../../api/generated',()=>({
  getAiProviderStatus:api.status,listAiProviderAlerts:api.alerts,disableAiProvider:api.disable,recheckAiProvider:api.recheck,acknowledgeAiProviderAlert:api.ack,
  getContentHealthReport:api.report,getReviewHealthReport:api.report,getSourceHealthReport:api.report,getReleaseHealthReport:api.report,getLearnerHealthReport:api.report,
}));
vi.mock('../../api/client/adminApi',()=>({unwrap:async(value:unknown)=>value}));

function renderPage(){const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});return render(<QueryClientProvider client={client}><ReportsPage/></QueryClientProvider>);}
beforeEach(()=>{api.report.mockResolvedValue({});api.status.mockResolvedValue({provider:'GEMINI',model:'gemini-2.5-flash',usageMode:'FREE_ONLY',expectedBillingTier:'FREE',usageLabel:'Application-tracked usage',authoritativeQuota:false,configurationValid:true,state:'QUOTA_PAUSED',reason:'AI_FREE_QUOTA_PAUSED',pausedUntil:'2026-07-22T07:00:00Z',usage:{minuteRequests:2,minuteInputTokens:200,dayRequests:8,dayInputTokens:900,dayOutputTokens:300},limits:{rpm:3,tpm:1000,rpd:10,inputTokensPerDay:1000,outputTokensPerDay:500}});api.alerts.mockResolvedValue([{id:'a1',severity:'CRITICAL',code:'AI_FREE_QUOTA_PAUSED',message:'Safety pause',createdAt:'2026-07-21T10:00:00Z'}]);api.disable.mockResolvedValue({});api.recheck.mockResolvedValue({});api.ack.mockResolvedValue(undefined);});

describe('ReportsPage provider operations',()=>{
  it('shows Gemini mode, application tracking, warning and non-guaranteed recheck without secrets',async()=>{renderPage();expect(await screen.findByText('gemini-2.5-flash')).toBeInTheDocument();expect(screen.getAllByText('Application-tracked usage').length).toBeGreaterThan(0);expect(screen.getByText(/not Google billing data/i)).toBeInTheDocument();expect(screen.getByText(/not a guaranteed provider reset/i)).toBeInTheDocument();expect(screen.getByRole('button',{name:/Acknowledge AI_FREE_QUOTA_PAUSED/i})).toBeInTheDocument();expect(document.body.textContent).not.toMatch(/api.?key|test-secret/i);});
  it('invokes audited disable, recheck and acknowledgement endpoints',async()=>{const user=userEvent.setup();renderPage();await screen.findByText('gemini-2.5-flash');await user.click(screen.getByRole('button',{name:'Disable provider'}));await user.click(screen.getByRole('button',{name:'Recheck safely'}));await user.click(screen.getByRole('button',{name:/Acknowledge AI_FREE_QUOTA_PAUSED/i}));expect(api.disable).toHaveBeenCalled();expect(api.recheck).toHaveBeenCalled();expect(api.ack).toHaveBeenCalledWith(expect.objectContaining({path:{alertId:'a1'}}));});
});
