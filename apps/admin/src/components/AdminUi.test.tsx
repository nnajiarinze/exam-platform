import { render, screen } from '@testing-library/react';
import { EmptyState, StatusBadge, TableFrame } from './AdminUi';

describe('admin UI primitives', () => {
  it('renders statuses with readable text instead of color alone', () => {
    render(<StatusBadge value="REQUIRES_UPDATE" />);
    expect(screen.getByText('REQUIRES UPDATE')).toBeInTheDocument();
  });

  it('announces empty states', () => {
    render(<EmptyState title="No content available" description="Create the first item." />);
    expect(screen.getByRole('status')).toHaveTextContent('No content available');
  });

  it('preserves semantic tables inside the responsive frame', () => {
    render(<TableFrame><table><caption>Questions</caption><tbody><tr><td>Example</td></tr></tbody></table></TableFrame>);
    expect(screen.getByRole('table', { name: 'Questions' })).toBeInTheDocument();
  });
});
