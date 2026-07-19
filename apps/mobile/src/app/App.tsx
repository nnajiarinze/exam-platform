import { NavigationContainer } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { RootNavigator } from '../navigation/RootNavigator';

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 2, staleTime: 30_000 }, mutations: { retry: 0 } } });

export default function App() {
  return <SafeAreaProvider><QueryClientProvider client={queryClient}><NavigationContainer><StatusBar style="dark" /><RootNavigator /></NavigationContainer></QueryClientProvider></SafeAreaProvider>;
}
