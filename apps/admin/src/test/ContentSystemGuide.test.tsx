import {render,screen,within} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {AuthProvider} from '../app/auth/AuthContext';
import {AppRouter} from '../app/router/AppRouter';
import {AdminRole} from '../app/permissions/permissions';
import {adminSessionStorageKey} from '../app/auth/authSession';

function renderGuide(role:AdminRole){sessionStorage.setItem(adminSessionStorageKey,JSON.stringify({id:`user-${role}`,displayName:'Guide user',roles:[role]}));const client=new QueryClient({defaultOptions:{queries:{retry:false}}});return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/help/content-system']}><AuthProvider><AppRouter/></AuthProvider></MemoryRouter></QueryClientProvider>)}

describe('How the Content System Works guide',()=>{
  it.each([AdminRole.ContentAuthor,AdminRole.ContentReviewer,AdminRole.ContentPublisher,AdminRole.Admin])('is readable by %s',role=>{renderGuide(role);expect(screen.getByRole('heading',{name:'How the Content System Works'})).toBeInTheDocument();expect(screen.getByRole('link',{name:'How the Content System Works'})).toBeInTheDocument();});
  it('renders the accessible relationship diagram and complete core definitions',()=>{renderGuide(AdminRole.Admin);expect(screen.getByLabelText('Content relationship and delivery flow')).toBeInTheDocument();expect(screen.getByText(/Text alternative: An exam contains an exam version/)).toBeInTheDocument();for(const heading of ['Exam','Exam Version','Knowledge Fact','Source','Question','Release','Practice Session','Mock Exam','Audit Event'])expect(screen.getByRole('heading',{name:heading})).toBeInTheDocument();});
  it('includes worked example, critical distinctions, governance transitions, glossary, and correct deep links',()=>{renderGuide(AdminRole.ContentReviewer);expect(screen.getByText('Exam — Swedish Citizenship')).toBeInTheDocument();const distinctions=screen.getByRole('heading',{name:'Most important distinctions'}).parentElement!;for(const term of ['Source','Fact','Question','Release'])expect(within(distinctions).getByText(term)).toBeInTheDocument();for(const term of ['Review','Validation','Publication','Delivery','Activation'])expect(screen.getAllByText(term).length).toBeGreaterThan(0);expect(screen.getByText('Content system terms and examples')).toBeInTheDocument();expect(screen.getByRole('link',{name:'Go to Sources'})).toHaveAttribute('href','/sources');expect(screen.getByRole('link',{name:'Go to Audit'})).toHaveAttribute('href','/audit');});
  it('is informational and exposes no guide write action',()=>{renderGuide(AdminRole.ContentAuthor);const main=screen.getByRole('main');expect(within(main).queryByRole('button')).not.toBeInTheDocument();expect(within(main).getAllByRole('table')).toHaveLength(3);});
});
