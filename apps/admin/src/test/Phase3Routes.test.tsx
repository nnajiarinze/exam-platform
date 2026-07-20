import { render,screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth/AuthContext';
import { AppRouter } from '../app/router/AppRouter';
import { AdminRole } from '../app/permissions/permissions';
import { adminSessionStorageKey } from '../app/auth/authSession';

const empty={items:[],page:0,size:20,totalItems:0,totalPages:0};
function renderRoute(path:string){sessionStorage.setItem(adminSessionStorageKey,JSON.stringify({id:'phase-3-admin',displayName:'Phase 3 Admin',roles:[AdminRole.Admin]}));const client=new QueryClient({defaultOptions:{queries:{retry:false}}});return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><AuthProvider><AppRouter/></AuthProvider></MemoryRouter></QueryClientProvider>);}
function json(body:object){return new Response(JSON.stringify(body),{status:200,headers:{'content-type':'application/json'}});}

describe('Phase 3 knowledge routes',()=>{
  it('loads and filters the knowledge fact list through the generated client',async()=>{const fetchMock=vi.fn().mockResolvedValue(json(empty));vi.stubGlobal('fetch',fetchMock);renderRoute('/knowledge');expect(screen.getByRole('heading',{name:'Knowledge facts'})).toBeInTheDocument();expect(await screen.findByText('No knowledge facts match these filters.')).toBeInTheDocument();expect((fetchMock.mock.calls[0][0]as Request).url).toContain('/api/v1/admin/knowledge-facts');});
  it('loads the learning objective list through the generated client',async()=>{const fetchMock=vi.fn().mockResolvedValue(json(empty));vi.stubGlobal('fetch',fetchMock);renderRoute('/knowledge/objectives');expect(screen.getByRole('heading',{name:'Learning objectives'})).toBeInTheDocument();expect(await screen.findByText('No learning objectives match these filters.')).toBeInTheDocument();expect((fetchMock.mock.calls[0][0]as Request).url).toContain('/api/v1/admin/learning-objectives');});
  it('renders the fact editor with objective and source pickers',async()=>{const fetchMock=vi.fn().mockImplementation(()=>Promise.resolve(json(empty)));vi.stubGlobal('fetch',fetchMock);renderRoute('/knowledge/facts/new');expect(screen.getByRole('heading',{name:'Create knowledge fact'})).toBeInTheDocument();expect(await screen.findByLabelText('Canonical statement')).toBeInTheDocument();expect(screen.getByText('No active sources are available. Create one under Sources first.')).toBeInTheDocument();});
});
