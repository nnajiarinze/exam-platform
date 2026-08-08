import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Alert } from 'react-native';
import { learningApi } from '../../api/learningApi';
import { useAuth } from '../auth/AuthContext';
import { DeleteAccountScreen, LinkedLoginsScreen } from './SettingsDetailScreens';

jest.mock('../../api/learningApi', () => ({ learningApi: {
  linkedLoginMethods: jest.fn(), initiateIdentityLink: jest.fn(), unlinkIdentityProvider: jest.fn(),
  logoutAllSessions: jest.fn(), beginAccountDeletion: jest.fn(), confirmAccountDeletion: jest.fn(),
} }));
jest.mock('../auth/AuthContext', () => ({ useAuth: jest.fn() }));

const mockedApi = jest.mocked(learningApi);
const mockedUseAuth = jest.mocked(useAuth);

function auth() {
  return {
    status: 'authenticated' as const, claims: { sub: 'stable-subject' }, requestReady: true,
    appleEnabled: true, googleEnabled: true, login: jest.fn(), register: jest.fn(), forgotPassword: jest.fn(),
    changePassword: jest.fn(), linkProvider: jest.fn(async () => true), reauthenticate: jest.fn(async () => true),
    logout: jest.fn(async () => undefined), clearError: jest.fn(),
  } as unknown as ReturnType<typeof useAuth>;
}

function screen(node: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

beforeEach(() => { jest.clearAllMocks(); mockedUseAuth.mockReturnValue(auth()); });

it('shows an existing Apple identity as linked without offering Link', async () => {
  mockedApi.linkedLoginMethods.mockResolvedValue({ ready: true, usableMethodCount: 3, methods: [
    { id: 'password', displayName: 'Password', linked: true, available: true },
    { id: 'google', displayName: 'Google', linked: true, available: true },
    { id: 'apple', displayName: 'Apple', linked: true, available: true },
  ] });
  const view = await screen(<LinkedLoginsScreen/>);
  await view.findByText('Apple');
  expect(view.getAllByText('Linked')).toHaveLength(3);
  expect(view.queryByRole('button', { name: 'Link' })).toBeNull();
  expect(view.getAllByRole('button', { name: 'Remove' })).toHaveLength(2);
});

it('links Google directly without reauthenticating the current Apple session', async () => {
  const before={ ready:true,usableMethodCount:1,methods:[
    {id:'password' as const,displayName:'Email and password',linked:false,available:true},
    {id:'google' as const,displayName:'Google',linked:false,available:true},
    {id:'apple' as const,displayName:'Apple',linked:true,available:true},
  ]};
  const after={...before,usableMethodCount:2,methods:before.methods.map(method=>method.id==='google'?{...method,linked:true}:method)};
  mockedApi.linkedLoginMethods.mockResolvedValueOnce(before).mockResolvedValueOnce(after);
  mockedApi.initiateIdentityLink.mockResolvedValueOnce({provider:'google',keycloakAction:'idp_link:google',correlationId:'correlation-1'});
  const currentAuth=auth();mockedUseAuth.mockReturnValue(currentAuth);
  const alert=jest.spyOn(Alert,'alert');
  const view=await screen(<LinkedLoginsScreen/>);await view.findByText('Apple');
  fireEvent.press(view.getByRole('button',{name:'Link'}));
  await waitFor(()=>expect(currentAuth.linkProvider).toHaveBeenCalledWith('google'));
  expect(currentAuth.reauthenticate).not.toHaveBeenCalled();
  expect(mockedApi.initiateIdentityLink).toHaveBeenCalledTimes(1);
  expect(alert).not.toHaveBeenCalledWith("Confirm it's you",expect.anything(),expect.anything(),expect.anything());
  alert.mockRestore();
});

it('keeps deletion behind reauthentication and explicit destructive confirmation', async () => {
  mockedApi.beginAccountDeletion.mockResolvedValue({ requestId: 'deletion-1', status: 'PENDING_CONFIRMATION' });
  const currentAuth = auth(); mockedUseAuth.mockReturnValue(currentAuth);
  const view = await screen(<DeleteAccountScreen/>);
  fireEvent.press(view.getByRole('button', { name: 'Verify identity and continue' }));
  await waitFor(() => expect(currentAuth.reauthenticate).toHaveBeenCalledTimes(1));
  expect(await view.findByLabelText('Type DELETE to confirm')).toBeTruthy();
  expect(view.getByRole('button', { name: 'Permanently delete my account' }).props.accessibilityState.disabled).toBe(true);
  expect(mockedApi.confirmAccountDeletion).not.toHaveBeenCalled();
});
