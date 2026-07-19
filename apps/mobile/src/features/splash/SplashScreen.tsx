import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect } from 'react';
import { Loading } from '../../components/ui';
import { Screen } from '../../components/Screen';
import type { RootStackParamList } from '../../navigation/types';
import { initialRouteFor } from '../../navigation/types';
import { useAppStore } from '../../app/store';

export function SplashScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'Splash'>) {
  const hydrated = useAppStore((s) => s.hydrated);
  const onboardingComplete = useAppStore((s) => s.onboardingComplete);
  useEffect(() => { if (hydrated) navigation.replace(initialRouteFor({ hydrated, onboardingComplete })); }, [hydrated, onboardingComplete, navigation]);
  return <Screen scroll={false}><Loading label="Starting Medbo…" /></Screen>;
}
