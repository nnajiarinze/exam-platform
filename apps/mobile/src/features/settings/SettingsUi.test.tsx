import { fireEvent, render } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';
import { theme } from '../../theme';
import { SettingsRow, SettingsSection } from './SettingsUi';
it('renders an accessible functional settings row',async()=>{const onPress=jest.fn();const view=await render(<SettingsSection title="Account"><SettingsRow icon="person" label="Edit Profile" detail="Learner name" onPress={onPress}/></SettingsSection>);fireEvent.press(view.getByLabelText('Edit Profile, Learner name'));expect(onPress).toHaveBeenCalledTimes(1);expect(view.getByText('Account')).toBeTruthy();});
it('keeps account deletion accessible with normal row styling',async()=>{const onPress=jest.fn();const view=await render(<SettingsSection title="Account management"><SettingsRow icon="delete" label="Delete Account" onPress={onPress}/></SettingsSection>);const row=view.getByRole('button',{name:'Delete Account'});fireEvent.press(row);expect(onPress).toHaveBeenCalledTimes(1);expect(view.getByText('Account management')).toBeTruthy();expect(StyleSheet.flatten(view.getByText('Delete Account').props.style).color).toBe(theme.colors.text);});
