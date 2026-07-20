import {render,screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {AuthProvider} from '../app/auth/AuthContext';
import {AppRouter} from '../app/router/AppRouter';
import {AdminRole} from '../app/permissions/permissions';
import {adminSessionStorageKey} from '../app/auth/authSession';
function json(body:object){return new Response(JSON.stringify(body),{status:200,headers:{'content-type':'application/json'}})}
function renderRoute(path:string){sessionStorage.setItem(adminSessionStorageKey,JSON.stringify({id:'publisher',displayName:'Publisher',roles:[AdminRole.Admin]}));const client=new QueryClient({defaultOptions:{queries:{retry:false}}});return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><AuthProvider><AppRouter/></AuthProvider></MemoryRouter></QueryClientProvider>)}
describe('Phase 6 release management',()=>{it('lists releases through the generated client',async()=>{const fetchMock=vi.fn().mockResolvedValue(json({items:[],page:0,size:20,totalItems:0,totalPages:0}));vi.stubGlobal('fetch',fetchMock);renderRoute('/releases');expect(screen.getByRole('heading',{name:'Releases'})).toBeInTheDocument();expect(await screen.findByText('No releases match these filters.')).toBeInTheDocument();expect((fetchMock.mock.calls[0][0] as Request).url).toContain('/api/v1/admin/releases')});it('renders the release creation workflow',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(json({items:[],page:0,size:100,totalItems:0,totalPages:0})));renderRoute('/releases/new');expect(screen.getByRole('heading',{name:'Create release'})).toBeInTheDocument();expect(await screen.findByLabelText('Exam')).toBeInTheDocument();expect(screen.getByLabelText('Release number')).toBeInTheDocument()})});
