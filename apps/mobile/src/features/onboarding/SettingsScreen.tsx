import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Body, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { LanguageForm } from './LanguageForm';

export function SettingsScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'Settings'>) {
  const state = useAppStore();
  return <Screen><Title>Settings</Title><Body>Update your language preferences.</Body><LanguageForm defaults={{ interfaceLanguage: state.interfaceLanguage, explanationLanguage: state.explanationLanguage }} onSubmit={(values) => { state.completeOnboarding(values.interfaceLanguage, values.explanationLanguage); navigation.goBack(); }} /></Screen>;
}
