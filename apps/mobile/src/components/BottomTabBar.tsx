import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { theme } from '../theme';
import { Icon } from './ui';

export type Tab = 'home' | 'topics' | 'exam' | 'progress' | 'settings';
export function BottomTabBar({ active, onNavigate }: { active: Tab; onNavigate: (tab: Tab) => void }) {
  const insets = useSafeAreaInsets();
  const tabs: { key: Tab; label: string }[] = active === 'settings'
    ? [{ key: 'home', label: 'Home' }, { key: 'topics', label: 'Study' }, { key: 'progress', label: 'Progress' }, { key: 'settings', label: 'Settings' }]
    : [{ key: 'home', label: 'Home' }, { key: 'topics', label: 'Study' }, { key: 'exam', label: 'Exam' }, { key: 'progress', label: 'Progress' }];
  return <View style={[styles.shell, { paddingBottom: Math.max(insets.bottom, 8) }]}>{tabs.map((tab) => {
    const selected = active === tab.key;
    return <Pressable accessibilityRole="tab" accessibilityState={{ selected }} accessibilityLabel={tab.label} key={tab.key} onPress={() => onNavigate(tab.key)} style={[styles.tab, selected && styles.active]}><Icon name={tab.key} size={23} color={selected ? theme.colors.text : theme.colors.muted} /><Text style={[styles.label, selected && styles.activeLabel]}>{tab.label}</Text></Pressable>;
  })}</View>;
}

const styles = StyleSheet.create({
  shell: { backgroundColor: theme.colors.surface, borderTopLeftRadius: theme.radii.xl, borderTopRightRadius: theme.radii.xl, bottom: 0, flexDirection: 'row', justifyContent: 'space-around', left: 0, paddingHorizontal: 8, paddingTop: 8, position: 'absolute', right: 0, ...theme.shadows.navigation },
  tab: { alignItems: 'center', borderRadius: theme.radii.full, gap: 2, justifyContent: 'center', minHeight: 54, minWidth: 70, paddingHorizontal: 12, paddingVertical: 5 },
  active: { backgroundColor: theme.colors.accent },
  label: { color: theme.colors.muted, ...theme.typography.caption }, activeLabel: { color: theme.colors.text },
});
