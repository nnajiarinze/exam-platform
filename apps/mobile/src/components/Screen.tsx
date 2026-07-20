import type { PropsWithChildren } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { theme } from '../theme/theme';

export function Screen({ children, scroll = true, bottomInset = false }: PropsWithChildren<{ scroll?: boolean; bottomInset?: boolean }>) {
  const content = <View style={styles.content}>{children}</View>;
  return <SafeAreaView style={styles.safe} edges={['top', 'left', 'right']}>{scroll ? <ScrollView contentContainerStyle={[styles.scroll, bottomInset && styles.bottomInset]} keyboardShouldPersistTaps="handled">{content}</ScrollView> : content}</SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: theme.colors.background },
  scroll: { flexGrow: 1 },
  content: { flex: 1, paddingHorizontal: theme.spacing.screen, paddingVertical: theme.spacing.sm, gap: theme.spacing.sm, width: '100%', maxWidth: 640, alignSelf: 'center' },
  bottomInset: { paddingBottom: 104 },
});
