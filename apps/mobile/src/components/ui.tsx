import type { PropsWithChildren, ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { theme } from '../theme';

export function Icon({ name, size = 24, color = theme.colors.primary }: { name: string; size?: number; color?: string }) {
  const glyphs: Record<string, string> = { back: '‹', search: '⌕', profile: '◎', play: '▶', shuffle: '⇄', progress: '▆', settings: '⚙', home: '⌂', topics: '▤', exam: '◷', arrow: '›', trophy: '★', help: '?', check: '✓', close: '×', info: 'i' };
  return <Text aria-hidden style={{ color, fontSize: size, lineHeight: size + 2, fontWeight: '700' }}>{glyphs[name] ?? name}</Text>;
}

export function Title({ children }: PropsWithChildren) { return <Text accessibilityRole="header" style={styles.title}>{children}</Text>; }
export function Body({ children }: PropsWithChildren) { return <Text style={styles.body}>{children}</Text>; }
export function Card({ children }: PropsWithChildren) { return <View style={styles.card}>{children}</View>; }
export function Button({ label, onPress, disabled = false, testID, variant = 'primary', icon }: { label: string; onPress: () => void; disabled?: boolean; testID?: string; variant?: 'primary' | 'secondary'; icon?: ReactNode }) {
  return <Pressable accessibilityRole="button" accessibilityState={{ disabled }} disabled={disabled} onPress={onPress} testID={testID} style={({ pressed }) => [styles.button, variant === 'secondary' && styles.secondaryButton, disabled && styles.disabled, pressed && styles.pressed]}>{icon}<Text style={[styles.buttonText, variant === 'secondary' && styles.secondaryButtonText]}>{label}</Text></Pressable>;
}
export function ProgressBar({ value, accessibilityLabel }: { value: number; accessibilityLabel?: string }) {
  const normalized = Math.max(0, Math.min(100, value));
  return <View accessibilityRole="progressbar" accessibilityLabel={accessibilityLabel} accessibilityValue={{ min: 0, max: 100, now: normalized }} style={styles.progressTrack}><View style={[styles.progressFill, { width: `${normalized}%` }]} /></View>;
}
export function StatusBadge({ label, tone = 'neutral' }: { label: string; tone?: 'neutral' | 'success' | 'error' }) {
  return <View style={[styles.badge, tone === 'success' && styles.successBadge, tone === 'error' && styles.errorBadge]}><Text style={[styles.badgeText, tone === 'success' && styles.successText, tone === 'error' && styles.errorText]}>{label}</Text></View>;
}
export function Loading({ label = 'Loading…' }: { label?: string }) { return <View style={styles.center}><ActivityIndicator color={theme.colors.primary} /><Text style={styles.stateText}>{label}</Text></View>; }
export function EmptyState({ message }: { message: string }) { return <Card><Text style={styles.stateText}>{message}</Text></Card>; }
export function ErrorState({ message, retry }: { message: string; retry?: () => void }) { return <Card><Text accessibilityRole="alert" style={styles.error}>{message}</Text>{retry && <Button label="Try again" onPress={retry} variant="secondary" />}</Card>; }

const styles = StyleSheet.create({
  title: { color: theme.colors.text, ...theme.typography.heading },
  body: { color: theme.colors.text, ...theme.typography.body },
  card: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderRadius: theme.radii.xl, borderWidth: 1, gap: theme.spacing.sm, padding: theme.spacing.sm, ...theme.shadows.card },
  button: { alignItems: 'center', backgroundColor: theme.colors.primary, borderColor: theme.colors.primary, borderRadius: theme.radii.lg, borderWidth: 2, flexDirection: 'row', gap: theme.spacing.xs, minHeight: 56, justifyContent: 'center', paddingHorizontal: theme.spacing.sm, paddingVertical: 12 },
  secondaryButton: { backgroundColor: 'transparent' },
  buttonText: { color: theme.colors.onPrimary, fontSize: 17, lineHeight: 24, fontWeight: '700' },
  secondaryButtonText: { color: theme.colors.primary },
  disabled: { backgroundColor: theme.colors.disabled, borderColor: theme.colors.disabled, opacity: 1 }, pressed: { opacity: 0.82 },
  center: { alignItems: 'center', flex: 1, gap: theme.spacing.sm, justifyContent: 'center', minHeight: 120 },
  stateText: { color: theme.colors.muted, ...theme.typography.body, textAlign: 'center' },
  error: { color: theme.colors.error, ...theme.typography.body },
  progressTrack: { backgroundColor: theme.colors.surfaceHigh, borderRadius: theme.radii.full, height: 10, overflow: 'hidden', width: '100%' },
  progressFill: { backgroundColor: theme.colors.accent, borderRadius: theme.radii.full, height: '100%' },
  badge: { alignSelf: 'flex-start', backgroundColor: theme.colors.surfaceHigh, borderRadius: theme.radii.full, paddingHorizontal: 12, paddingVertical: 5 },
  badgeText: { color: theme.colors.primary, ...theme.typography.caption },
  successBadge: { backgroundColor: theme.colors.successBackground }, successText: { color: theme.colors.success },
  errorBadge: { backgroundColor: theme.colors.errorBackground }, errorText: { color: theme.colors.error },
});
