import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { AppRouter } from './router/AppRouter';
import { createQueryClient } from './providers/queryClient';

const queryClient = createQueryClient();
export function App() { return <QueryClientProvider client={queryClient}><BrowserRouter><AuthProvider><AppRouter /></AuthProvider></BrowserRouter></QueryClientProvider>; }
