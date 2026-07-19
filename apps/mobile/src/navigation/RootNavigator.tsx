import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { HomeScreen } from '../features/home/HomeScreen';
import { OnboardingScreen } from '../features/onboarding/OnboardingScreen';
import { SettingsScreen } from '../features/onboarding/SettingsScreen';
import { PracticeSetupScreen } from '../features/practice/PracticeSetupScreen';
import { QuestionScreen } from '../features/practice/QuestionScreen';
import { SessionCompleteScreen } from '../features/practice/SessionCompleteScreen';
import { ProgressScreen } from '../features/progress/ProgressScreen';
import { SplashScreen } from '../features/splash/SplashScreen';
import { TopicsScreen } from '../features/topics/TopicsScreen';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  return <Stack.Navigator initialRouteName="Splash" screenOptions={{ headerBackTitle: 'Back' }}>
    <Stack.Screen name="Splash" component={SplashScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Onboarding" component={OnboardingScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Home" component={HomeScreen} options={{ headerBackVisible: false }} />
    <Stack.Screen name="Topics" component={TopicsScreen} />
    <Stack.Screen name="PracticeSetup" component={PracticeSetupScreen} options={{ title: 'Practice setup' }} />
    <Stack.Screen name="Question" component={QuestionScreen} options={{ headerBackVisible: false }} />
    <Stack.Screen name="SessionComplete" component={SessionCompleteScreen} options={{ title: 'Session complete', headerBackVisible: false }} />
    <Stack.Screen name="Progress" component={ProgressScreen} />
    <Stack.Screen name="Settings" component={SettingsScreen} />
  </Stack.Navigator>;
}
