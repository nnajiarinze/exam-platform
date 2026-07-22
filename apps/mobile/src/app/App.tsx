import { NavigationContainer } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { RootNavigator } from '../navigation/RootNavigator';
import { AuthProvider } from '../features/auth/AuthContext';

const nonRetryableCodes = new Set(['AUTHENTICATION_REQUIRED','FORBIDDEN','VALIDATION_ERROR','PRACTICE_SESSION_NOT_FOUND']);
const queryClient = new QueryClient({ defaultOptions: { queries: {
  retry: (failureCount,error) => failureCount < 10 && !nonRetryableCodes.has((error as {code?:string})?.code ?? ''),
  retryDelay: attempt => Math.min(5_000 * 2 ** attempt, 15_000),
  staleTime: 30_000,
}, mutations: { retry: 0 } } });

export default function App() {
  return <SafeAreaProvider><QueryClientProvider client={queryClient}><AuthProvider><NavigationContainer><StatusBar style="dark" /><RootNavigator /></NavigationContainer></AuthProvider></QueryClientProvider></SafeAreaProvider>;
}
