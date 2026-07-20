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

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  return <Stack.Navigator initialRouteName="Splash" screenOptions={{ headerBackTitle: 'Back', headerStyle: { backgroundColor: '#F8F9FF' }, headerTintColor: '#02458B', headerShadowVisible: false }}>
    <Stack.Screen name="Splash" component={SplashScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Onboarding" component={OnboardingScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Home" component={HomeScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Topics" component={TopicsScreen} options={{ headerShown: false }} />
    <Stack.Screen name="PracticeSetup" component={PracticeSetupScreen} options={{ title: 'Practice setup' }} />
    <Stack.Screen name="Question" component={QuestionScreen} options={{ headerShown: false }} />
    <Stack.Screen name="SessionComplete" component={SessionCompleteScreen} options={{ headerShown: false }} />
    <Stack.Screen name="Progress" component={ProgressScreen} />
    <Stack.Screen name="Settings" component={SettingsScreen} />
    <Stack.Screen name="MockExam" component={MockExamScreen} options={{ title: 'Mock examination' }} />
    <Stack.Screen name="MockQuestion" component={MockQuestionScreen} options={{ title: 'Mock examination', headerBackVisible: false }} />
    <Stack.Screen name="MockResults" component={MockResultsScreen} options={{ title: 'Mock results', headerBackVisible: false }} />
    <Stack.Screen name="MockAnswerReview" component={MockAnswerReviewScreen} options={{ title: 'Answer review' }} />
    <Stack.Screen name="MockHistory" component={MockHistoryScreen} options={{ title: 'Mock history' }} />
  </Stack.Navigator>;
}
