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
import { MockExamScreen } from '../features/mockexam/MockExamScreen';
import { MockQuestionScreen } from '../features/mockexam/MockQuestionScreen';
import { MockResultsScreen } from '../features/mockexam/MockResultsScreen';
import { MockHistoryScreen } from '../features/mockexam/MockHistoryScreen';
import { MockAnswerReviewScreen } from '../features/mockexam/MockAnswerReviewScreen';
import type { RootStackParamList } from './types';
import { useAuth } from '../features/auth/AuthContext';
import { useAppStore } from '../app/store';
import { ForgotPasswordScreen, LoginScreen, RegisterScreen, SessionExpiredScreen, VerificationPendingScreen, WelcomeScreen } from '../features/auth/AuthScreens';
import { ProfileScreen } from '../features/auth/ProfileScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  const auth=useAuth();const onboardingComplete=useAppStore(s=>s.onboardingComplete);
  if(auth.status==='restoring')return <Stack.Navigator screenOptions={{headerShown:false}}><Stack.Screen name="Splash" component={SplashScreen}/></Stack.Navigator>;
  if(auth.status==='unauthenticated')return <Stack.Navigator screenOptions={{ headerStyle: { backgroundColor: '#F8F9FF' }, headerTintColor: '#02458B', headerShadowVisible: false }}><Stack.Screen name="Welcome" component={WelcomeScreen} options={{headerShown:false}}/><Stack.Screen name="Login" component={LoginScreen}/><Stack.Screen name="Register" component={RegisterScreen}/><Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen}/></Stack.Navigator>;
  if(auth.status==='verification-required')return <Stack.Navigator screenOptions={{headerShown:false}}><Stack.Screen name="VerificationPending" component={VerificationPendingScreen}/></Stack.Navigator>;
  if(auth.status==='expired')return <Stack.Navigator screenOptions={{headerShown:false}}><Stack.Screen name="SessionExpired" component={SessionExpiredScreen}/></Stack.Navigator>;
  return <Stack.Navigator initialRouteName={onboardingComplete?'Home':'Onboarding'} screenOptions={{ headerBackTitle: 'Back', headerStyle: { backgroundColor: '#F8F9FF' }, headerTintColor: '#02458B', headerShadowVisible: false }}>
    <Stack.Screen name="Onboarding" component={OnboardingScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Home" component={HomeScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Topics" component={TopicsScreen} options={{ headerShown: false }} />
    <Stack.Screen name="PracticeSetup" component={PracticeSetupScreen} options={{ title: 'Practice setup' }} />
    <Stack.Screen name="Question" component={QuestionScreen} options={{ headerShown: false }} />
    <Stack.Screen name="SessionComplete" component={SessionCompleteScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Progress" component={ProgressScreen} />
    <Stack.Screen name="Settings" component={SettingsScreen} />
    <Stack.Screen name="Profile" component={ProfileScreen} />
    <Stack.Screen name="MockExam" component={MockExamScreen} options={{ title: 'Mock examination' }} />
    <Stack.Screen name="MockQuestion" component={MockQuestionScreen} options={{ title: 'Mock examination', headerBackVisible: false }} />
    <Stack.Screen name="MockResults" component={MockResultsScreen} options={{ title: 'Mock results', headerBackVisible: false }} />
    <Stack.Screen name="MockAnswerReview" component={MockAnswerReviewScreen} options={{ title: 'Answer review' }} />
    <Stack.Screen name="MockHistory" component={MockHistoryScreen} options={{ title: 'Mock history' }} />
  </Stack.Navigator>;
}
