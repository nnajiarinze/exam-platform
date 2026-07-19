import { fireEvent, render } from '@testing-library/react-native';
import { TopicList } from './TopicList';
it('renders backend topics and selects one', async () => { const onSelect = jest.fn(); const view = await render(<TopicList subjects={[{ id: 's1', name: 'Society', topics: [{ id: 't1', name: 'Democracy' }] }]} onSelect={onSelect} />); fireEvent.press(view.getByText('Democracy')); expect(onSelect).toHaveBeenCalledWith('t1', 'Democracy'); });
