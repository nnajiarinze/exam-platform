import type { PropsWithChildren } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Icon } from '../../components/ui';
import { theme } from '../../theme';

export function SettingsSection({ title, children }: PropsWithChildren<{ title: string }>) {
  return <View style={styles.section}><Text style={styles.heading}>{title}</Text><View style={styles.card}>{children}</View></View>;
}

export function SettingsRow({ icon, label, detail, onPress, destructive=false }: { icon: string; label: string; detail?: string; onPress: () => void; destructive?: boolean }) {
  return <Pressable accessibilityRole="button" accessibilityLabel={detail ? `${label}, ${detail}` : label} onPress={onPress} style={({pressed})=>[styles.row,pressed&&styles.pressed]}>
    <View style={[styles.iconBox,destructive&&styles.destructiveBox]}><Icon name={icon} size={22} color={destructive?theme.colors.error:theme.colors.primary}/></View>
    <View style={styles.copy}><Text style={[styles.label,destructive&&styles.destructive]}>{label}</Text>{detail?<Text style={styles.detail}>{detail}</Text>:null}</View>
    <Icon name="arrow" size={26} color={theme.colors.muted}/>
  </Pressable>;
}

export function Divider(){return <View style={styles.divider}/>;}

const styles=StyleSheet.create({section:{gap:theme.spacing.xs},heading:{color:theme.colors.primary,...theme.typography.label,letterSpacing:1.1,marginLeft:4,textTransform:'uppercase'},card:{backgroundColor:theme.colors.surface,borderRadius:theme.radii.xl,overflow:'hidden',...theme.shadows.card},row:{alignItems:'center',flexDirection:'row',gap:theme.spacing.sm,minHeight:82,paddingHorizontal:theme.spacing.sm,paddingVertical:theme.spacing.xs},pressed:{backgroundColor:theme.colors.surfaceHigh},iconBox:{alignItems:'center',backgroundColor:theme.colors.surfaceHigh,borderRadius:theme.radii.md,height:48,justifyContent:'center',width:48},destructiveBox:{backgroundColor:theme.colors.errorBackground},copy:{flex:1,gap:2},label:{color:theme.colors.text,...theme.typography.body},detail:{color:theme.colors.muted,...theme.typography.caption},destructive:{color:theme.colors.error},divider:{backgroundColor:theme.colors.divider,height:1,marginHorizontal:theme.spacing.sm}});
