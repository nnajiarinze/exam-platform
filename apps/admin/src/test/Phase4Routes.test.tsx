import { render,screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth/AuthContext';
import { AppRouter } from '../app/router/AppRouter';
import { AdminRole } from '../app/permissions/permissions';
import { adminSessionStorageKey } from '../app/auth/authSession';

const empty={items:[],page:0,size:20,totalItems:0,totalPages:0};
function renderRoute(path:string){sessionStorage.setItem(adminSessionStorageKey,JSON.stringify({id:'phase-4-admin',displayName:'Phase 4 Admin',roles:[AdminRole.Admin]}));const client=new QueryClient({defaultOptions:{queries:{retry:false}}});return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><AuthProvider><AppRouter/></AuthProvider></MemoryRouter></QueryClientProvider>);}
function json(body:object){return new Response(JSON.stringify(body),{status:200,headers:{'content-type':'application/json'}});}

describe('Phase 4 question routes',()=>{
  it('loads the searchable question list through the generated client',async()=>{const fetchMock=vi.fn().mockResolvedValue(json(empty));vi.stubGlobal('fetch',fetchMock);renderRoute('/questions');expect(screen.getByRole('heading',{name:'Questions'})).toBeInTheDocument();expect(await screen.findByText('No questions match these filters.')).toBeInTheDocument();expect((fetchMock.mock.calls[0][0]as Request).url).toContain('/api/v1/admin/questions');});
  it('renders the editor, approved fact picker, option editor, and preview',async()=>{vi.stubGlobal('fetch',vi.fn().mockImplementation(()=>Promise.resolve(json(empty))));renderRoute('/questions/new');expect(screen.getByRole('heading',{name:'Create question'})).toBeInTheDocument();expect(await screen.findByLabelText('Question text')).toBeInTheDocument();expect(screen.getByRole('group',{name:'Approved knowledge facts'})).toBeInTheDocument();expect(screen.getByRole('group',{name:'Answer options'})).toBeInTheDocument();expect(screen.getByRole('heading',{name:'Preview'})).toBeInTheDocument();});
});
