import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { ReactNode } from 'react';
import { ActivityIndicator, Alert, Linking, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Screen } from '../../components/Screen';
import { Body, Button, Card, Title } from '../../components/ui';
import { appConfig } from '../../api/config';
import type { RootStackParamList } from '../../navigation/types';
import { theme } from '../../theme';
import { useAuth, type AuthDiagnosticCode, type AuthIntent } from './AuthContext';

const errorCopy: Record<AuthDiagnosticCode, string> = {
  AUTH_CANCELLED: 'Inloggningen avbröts. Du kan försöka igen när du vill.',
  AUTH_NETWORK_UNAVAILABLE: 'Det går inte att nå den säkra inloggningen just nu. Kontrollera anslutningen och försök igen.',
  AUTH_PROVIDER_UNAVAILABLE: 'Den valda inloggningsmetoden är inte tillgänglig just nu.',
  AUTH_CALLBACK_INVALID: 'Inloggningssvaret kunde inte verifieras. Försök igen från början.',
  AUTH_TOKEN_EXCHANGE_FAILED: 'Inloggningen kunde inte slutföras. Ingen information har sparats.',
  AUTH_SESSION_EXPIRED: 'Din session har gått ut. Logga in igen för att fortsätta.',
  AUTH_CONFIGURATION_INVALID: 'Den säkra inloggningen förbereds fortfarande. Försök igen om ett ögonblick.',
};

function AuthLayout({ children }: { children: ReactNode }) {
  return <Screen><View style={styles.hero}><View style={styles.mark}><Text style={styles.crown}>M</Text></View><Text style={styles.brand}>Medbo</Text><Text style={styles.eyebrow}>SVERIGE I FOKUS</Text><Text accessibilityRole="header" style={styles.heroTitle}>Förbered dig för medborgarskapsprovet</Text><Text style={styles.heroCopy}>Lär dig i din takt, öva på viktiga frågor och gör realistiska prov – med dina framsteg tryggt sparade.</Text></View>{children}</Screen>;
}

function ProviderButton({ label, kind, disabled, busy, onPress }: { label: string; kind: 'apple' | 'google' | 'email'; disabled?: boolean; busy?: boolean; onPress: () => void }) {
  return <Pressable accessibilityRole="button" accessibilityLabel={label} accessibilityState={{ disabled: Boolean(disabled), busy: Boolean(busy) }} disabled={disabled} onPress={onPress} testID={`auth-${kind}`} style={({ pressed }) => [styles.providerButton, kind === 'apple' && styles.appleButton, pressed && styles.pressed, disabled && styles.disabled]}>
    <View style={styles.providerIcon}>{busy ? <ActivityIndicator color={kind === 'apple' ? '#fff' : theme.colors.primary}/> : <Text style={[styles.providerGlyph, kind === 'apple' && styles.appleText]}>{kind === 'apple' ? '●' : kind === 'google' ? 'G' : '@'}</Text>}</View>
    <Text style={[styles.providerLabel, kind === 'apple' && styles.appleText]}>{label}</Text><View style={styles.providerIcon}/>
  </Pressable>;
}

function LegalLink({ label, url }: { label: string; url?: string }) {
  const open = () => url ? void Linking.openURL(url) : Alert.alert(`${label} saknas`, 'Länken är ännu inte publicerad för den här miljön.');
  return <Pressable accessibilityRole="link" onPress={open} hitSlop={10}><Text style={styles.legalLink}>{label}</Text></Pressable>;
}

export function WelcomeScreen(_: NativeStackScreenProps<RootStackParamList, 'Welcome'>) {
  const auth = useAuth();
  const busy = auth.status === 'authenticating';
  const active = auth.activeIntent;
  const isApplePlatform = Platform.OS === 'ios';
  const invoke = (intent: AuthIntent) => intent === 'register' ? auth.register() : auth.login(intent as 'apple' | 'google' | 'email');
  return <AuthLayout><Card style={styles.authCard}><Text style={styles.cardTitle}>Fortsätt till Medbo</Text><Text style={styles.secureNote}>Ett säkert inloggningsfönster öppnas och återvänder automatiskt till appen.</Text>
    {auth.diagnosticCode ? <View accessibilityRole="alert" style={styles.errorBox}><Text style={styles.errorText}>{errorCopy[auth.diagnosticCode]}</Text><Pressable onPress={auth.clearError}><Text style={styles.retry}>Försök igen</Text></Pressable></View> : null}
    <View style={styles.actions}>
      {isApplePlatform ? <ProviderButton kind="apple" label={auth.appleEnabled ? 'Fortsätt med Apple' : 'Apple är inte konfigurerat'} disabled={busy || !auth.requestReady || !auth.appleEnabled} busy={active === 'apple'} onPress={() => void invoke('apple')}/> : null}
      <ProviderButton kind="google" label={auth.googleEnabled ? 'Fortsätt med Google' : 'Google är inte konfigurerat'} disabled={busy || !auth.requestReady || !auth.googleEnabled} busy={active === 'google'} onPress={() => void invoke('google')}/>
      <ProviderButton kind="email" label="Fortsätt med e-post" disabled={busy || !auth.requestReady} busy={active === 'email'} onPress={() => void invoke('email')}/>
      <View style={styles.divider}><View style={styles.line}/><Text style={styles.or}>eller</Text><View style={styles.line}/></View>
      <Button label={active === 'register' ? 'Öppnar konto…' : 'Skapa konto'} variant="secondary" disabled={busy || !auth.requestReady} onPress={() => void invoke('register')}/>
    </View>
  </Card><View style={styles.legal}><LegalLink label="Integritet" url={appConfig.privacyUrl}/><Text style={styles.dot}>•</Text><LegalLink label="Villkor" url={appConfig.termsUrl}/><Text style={styles.dot}>•</Text><LegalLink label="Hjälp" url={appConfig.helpUrl}/></View></AuthLayout>;
}

export function LoginScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'Login'>) { const auth = useAuth(); return <AuthLayout><Card><Title>Logga in med e-post</Title><Body>Ditt lösenord anges bara i det säkra inloggningsfönstret och delas aldrig med appen.</Body><Button label="Fortsätt med e-post" disabled={!auth.requestReady || auth.status === 'authenticating'} onPress={() => void auth.login('email')}/><Button label="Glömt lösenord?" variant="text" onPress={() => navigation.navigate('ForgotPassword')}/></Card></AuthLayout>; }
export function RegisterScreen() { const auth = useAuth(); return <AuthLayout><Card><Title>Skapa konto</Title><Body>Registrering och e-postverifiering sker i det säkra inloggningsfönstret.</Body><Button label="Fortsätt till registrering" disabled={!auth.requestReady || auth.status === 'authenticating'} onPress={() => void auth.register()}/></Card></AuthLayout>; }
export function ForgotPasswordScreen() { const auth = useAuth(); return <AuthLayout><Card><Title>Återställ lösenord</Title><Body>Vi visar inte om en viss e-postadress finns registrerad.</Body><Button label="Fortsätt säkert" disabled={!auth.requestReady || auth.status === 'authenticating'} onPress={() => void auth.forgotPassword()}/></Card></AuthLayout>; }
export function VerificationPendingScreen() { const auth = useAuth(); return <AuthLayout><Card><Title>Verifiera din e-post</Title><Body>Öppna verifieringsmeddelandet och logga sedan in igen.</Body><Button label="Logga in efter verifiering" onPress={() => void auth.login('email')}/><Button label="Använd ett annat konto" variant="secondary" onPress={() => void auth.logout()}/></Card></AuthLayout>; }
export function SessionExpiredScreen() { const auth = useAuth(); return <AuthLayout><Card><Title>Sessionen har gått ut</Title><Body>Dina sparade framsteg finns kvar. Logga in igen för att fortsätta.</Body><Button label="Logga in igen" onPress={auth.clearError}/><Button label="Logga ut" variant="secondary" onPress={() => void auth.logout()}/></Card></AuthLayout>; }

const styles = StyleSheet.create({
  hero:{alignItems:'center',gap:theme.spacing.xs,paddingHorizontal:theme.spacing.sm,paddingTop:theme.spacing.lg},mark:{alignItems:'center',backgroundColor:theme.colors.primary,borderRadius:24,height:48,justifyContent:'center',width:48},crown:{color:theme.colors.onPrimary,fontSize:25,fontWeight:'900'},brand:{color:theme.colors.primary,fontSize:25,fontWeight:'900'},eyebrow:{color:theme.colors.accentStrong,fontSize:12,fontWeight:'800',letterSpacing:1.6,marginTop:theme.spacing.xs},heroTitle:{color:theme.colors.text,fontSize:30,fontWeight:'800',letterSpacing:-.6,lineHeight:36,textAlign:'center'},heroCopy:{color:theme.colors.muted,fontSize:16,lineHeight:23,maxWidth:500,textAlign:'center'},authCard:{gap:theme.spacing.sm,marginTop:theme.spacing.sm},cardTitle:{color:theme.colors.text,fontSize:19,fontWeight:'800',textAlign:'center'},secureNote:{color:theme.colors.muted,fontSize:13,lineHeight:18,textAlign:'center'},actions:{gap:theme.spacing.sm},providerButton:{alignItems:'center',backgroundColor:theme.colors.surface,borderColor:theme.colors.border,borderRadius:theme.radii.lg,borderWidth:1,flexDirection:'row',height:56,justifyContent:'space-between',paddingHorizontal:16},appleButton:{backgroundColor:'#000',borderColor:'#000'},providerIcon:{alignItems:'center',justifyContent:'center',width:26},providerGlyph:{color:theme.colors.primary,fontSize:19,fontWeight:'900'},providerLabel:{color:theme.colors.text,fontSize:16,fontWeight:'700'},appleText:{color:'#fff'},pressed:{opacity:.76},disabled:{opacity:.46},divider:{alignItems:'center',flexDirection:'row',gap:10},line:{backgroundColor:theme.colors.divider,flex:1,height:1},or:{color:theme.colors.muted,fontSize:13},errorBox:{backgroundColor:theme.colors.errorBackground,borderRadius:theme.radii.md,gap:6,padding:12},errorText:{color:theme.colors.error,fontSize:14,lineHeight:19},retry:{color:theme.colors.primary,fontSize:14,fontWeight:'800'},legal:{alignItems:'center',flexDirection:'row',gap:10,justifyContent:'center',paddingBottom:theme.spacing.md},legalLink:{color:theme.colors.primary,fontSize:13,fontWeight:'700',textDecorationLine:'underline'},dot:{color:theme.colors.muted},
});
