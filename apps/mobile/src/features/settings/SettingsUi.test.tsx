import { fireEvent, render } from '@testing-library/react-native';
import { SettingsRow, SettingsSection } from './SettingsUi';
it('renders an accessible functional settings row',async()=>{const onPress=jest.fn();const view=await render(<SettingsSection title="Account"><SettingsRow icon="person" label="Edit Profile" detail="Learner name" onPress={onPress}/></SettingsSection>);fireEvent.press(view.getByLabelText('Edit Profile, Learner name'));expect(onPress).toHaveBeenCalledTimes(1);expect(view.getByText('Account')).toBeTruthy();});
