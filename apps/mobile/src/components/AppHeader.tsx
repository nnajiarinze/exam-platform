import { Pressable, StyleSheet, Text, View } from 'react-native';
import { theme } from '../theme';
import { Icon } from './ui';

export function AppHeader({ onBack, action = 'profile', onAction }: { onBack?: () => void; action?: 'profile' | 'search' | 'help' | 'none'; onAction?: () => void }) {
  return <View style={styles.header}>
    <View style={styles.leading}>{onBack && <Pressable accessibilityRole="button" accessibilityLabel="Go back" hitSlop={10} onPress={onBack} style={styles.iconButton}><Icon name="back" size={36} /></Pressable>}<Text accessibilityRole="header" style={styles.brand}>Svea Study</Text></View>
    {action !== 'none' && <Pressable accessibilityRole="button" accessibilityLabel={action === 'search' ? 'Search' : action === 'help' ? 'Help' : 'Profile'} hitSlop={10} onPress={onAction} style={styles.iconButton}><Icon name={action} size={action === 'help' ? 22 : 28} color={theme.colors.text} /></Pressable>}
  </View>;
}

const styles = StyleSheet.create({
  header: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', minHeight: 56 },
  leading: { alignItems: 'center', flexDirection: 'row', gap: 10 },
  brand: { color: theme.colors.primary, fontSize: 25, lineHeight: 32, fontWeight: '700' },
  iconButton: { alignItems: 'center', borderRadius: theme.radii.full, justifyContent: 'center', minHeight: 44, minWidth: 44 },
});
