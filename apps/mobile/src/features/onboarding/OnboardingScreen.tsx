import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Body, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { LanguageForm } from './LanguageForm';

export function OnboardingScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'Onboarding'>) {
  const complete = useAppStore((s) => s.completeOnboarding);
  return <Screen><Title>Welcome</Title><Body>Choose how the app and explanations should be displayed.</Body><LanguageForm defaults={{ interfaceLanguage: 'en', explanationLanguage: 'en' }} onSubmit={(values) => { complete(values.interfaceLanguage, values.explanationLanguage); navigation.replace('Home'); }} /></Screen>;
}
