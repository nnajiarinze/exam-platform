import { render, screen } from '@testing-library/react';
import { ApiError } from '../api/errors/ApiError';
import { AsyncState } from './AsyncState';

describe('AsyncState error actions', () => {
  it('distinguishes authentication and exposes safe recovery information', () => {
    render(<AsyncState loading={false} error={new ApiError('AUTHENTICATION','Sign in again',401,'AUTHENTICATION_REQUIRED',[],'request-9')}><span>content</span></AsyncState>);
    expect(screen.getByRole('alert')).toHaveTextContent('Sign-in required');
    expect(screen.getByText('Request ID: request-9')).toBeInTheDocument();
    expect(screen.getByRole('link',{name:'Re-authenticate'})).toHaveAttribute('href','/login');
    expect(screen.getByRole('button',{name:'Retry'})).toBeInTheDocument();
  });

  it('distinguishes backend unavailability', () => {
    render(<AsyncState loading={false} error={new ApiError('NETWORK','Offline')}><span>content</span></AsyncState>);
    expect(screen.getByRole('alert')).toHaveTextContent('Backend unavailable');
    expect(screen.queryByRole('link',{name:'Re-authenticate'})).not.toBeInTheDocument();
  });
});
