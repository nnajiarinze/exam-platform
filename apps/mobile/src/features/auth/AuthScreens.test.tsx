import { fireEvent, render } from '@testing-library/react-native';
import { WelcomeScreen } from './AuthScreens';
import { useAuth } from './AuthContext';

jest.mock('./AuthContext', () => ({ useAuth: jest.fn() }));
const mockedUseAuth = jest.mocked(useAuth);

function auth(overrides: Record<string, unknown> = {}) {
  return {
    status: 'unauthenticated', claims: undefined, activeIntent: undefined, diagnosticCode: undefined,
    requestReady: true, appleEnabled: true, googleEnabled: true,
    login: jest.fn(async () => undefined), register: jest.fn(async () => undefined), forgotPassword: jest.fn(async () => undefined),
    changePassword: jest.fn(async () => undefined), logout: jest.fn(async () => undefined), clearError: jest.fn(), ...overrides,
  } as ReturnType<typeof useAuth>;
}

it('renders a branded, provider-specific, single-session authentication welcome', async () => {
  const initial = auth(); mockedUseAuth.mockReturnValue(initial);
  const view = await render(<WelcomeScreen navigation={{} as never} route={{} as never}/>);
  expect(view.getByText('Förbered dig för medborgarskapsprovet')).toBeTruthy();
  expect(view.getByRole('button', { name: 'Fortsätt med Apple' })).toBeTruthy();
  expect(view.getByRole('button', { name: 'Fortsätt med Google' })).toBeTruthy();
  expect(view.getByRole('button', { name: 'Fortsätt med e-post' })).toBeTruthy();
  expect(view.getByRole('button', { name: 'Skapa konto' })).toBeTruthy();
  fireEvent.press(view.getByTestId('auth-google')); fireEvent.press(view.getByTestId('auth-email'));
  expect(initial.login).toHaveBeenNthCalledWith(1, 'google'); expect(initial.login).toHaveBeenNthCalledWith(2, 'email');

  mockedUseAuth.mockReturnValue(auth({ status: 'authenticating', activeIntent: 'google', appleEnabled: false }));
  await view.rerender(<WelcomeScreen navigation={{} as never} route={{} as never}/>);
  expect(view.getByTestId('auth-apple').props.accessibilityState.disabled).toBe(true);
  expect(view.getByTestId('auth-google').props.accessibilityState.busy).toBe(true);
  expect(view.getByTestId('auth-email').props.accessibilityState.disabled).toBe(true);
  await view.unmount();
});
