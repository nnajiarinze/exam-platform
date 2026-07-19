import type { PropsWithChildren } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { theme } from '../theme/theme';

export function Title({ children }: PropsWithChildren) { return <Text accessibilityRole="header" style={styles.title}>{children}</Text>; }
export function Body({ children }: PropsWithChildren) { return <Text style={styles.body}>{children}</Text>; }
export function Card({ children }: PropsWithChildren) { return <View style={styles.card}>{children}</View>; }
export function Button({ label, onPress, disabled = false, testID }: { label: string; onPress: () => void; disabled?: boolean; testID?: string }) {
  return <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress} testID={testID} style={({ pressed }) => [styles.button, disabled && styles.disabled, pressed && styles.pressed]}><Text style={styles.buttonText}>{label}</Text></Pressable>;
}
export function Loading({ label = 'Loading…' }: { label?: string }) { return <View style={styles.center}><ActivityIndicator /><Text>{label}</Text></View>; }
export function ErrorState({ message, retry }: { message: string; retry?: () => void }) { return <Card><Text accessibilityRole="alert" style={styles.error}>{message}</Text>{retry && <Button label="Try again" onPress={retry} />}</Card>; }

const styles = StyleSheet.create({
  title: { color: theme.colors.text, fontSize: 28, fontWeight: '700' },
  body: { color: theme.colors.text, fontSize: 17, lineHeight: 24 },
  card: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderRadius: 12, borderWidth: 1, gap: 12, padding: 16 },
  button: { alignItems: 'center', backgroundColor: theme.colors.primary, borderRadius: 10, minHeight: 48, justifyContent: 'center', padding: 12 },
  buttonText: { color: theme.colors.primaryText, fontSize: 16, fontWeight: '600' },
  disabled: { opacity: 0.45 }, pressed: { opacity: 0.8 }, center: { alignItems: 'center', flex: 1, gap: 12, justifyContent: 'center' },
  error: { color: theme.colors.error, fontSize: 16 },
});
