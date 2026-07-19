import { Controller, useForm } from 'react-hook-form';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Language } from '../../app/store';
import { Button } from '../../components/ui';
import { theme } from '../../theme/theme';

type Values = { interfaceLanguage: Language; explanationLanguage: Language };
export function LanguageForm({ defaults, onSubmit }: { defaults: Values; onSubmit: (values: Values) => void }) {
  const { control, handleSubmit } = useForm<Values>({ defaultValues: defaults });
  return <View style={styles.form}>
    <LanguageField control={control} name="interfaceLanguage" label="Interface language" />
    <LanguageField control={control} name="explanationLanguage" label="Explanation language" />
    <Button label="Save and continue" onPress={handleSubmit(onSubmit)} />
  </View>;
}

function LanguageField({ control, name, label }: { control: ReturnType<typeof useForm<Values>>['control']; name: keyof Values; label: string }) {
  return <Controller control={control} name={name} render={({ field }) => <View style={styles.group}><Text style={styles.label}>{label}</Text><View style={styles.row}>{(['en', 'sv'] as Language[]).map((language) => <Pressable accessibilityRole="radio" accessibilityState={{ checked: field.value === language }} key={language} onPress={() => field.onChange(language)} style={[styles.choice, field.value === language && styles.selected]}><Text>{language === 'en' ? 'English' : 'Svenska'}</Text></Pressable>)}</View></View>} />;
}
const styles = StyleSheet.create({ form: { gap: 24 }, group: { gap: 8 }, label: { fontSize: 17, fontWeight: '600' }, row: { flexDirection: 'row', gap: 8 }, choice: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderRadius: 8, borderWidth: 1, flex: 1, padding: 14 }, selected: { borderColor: theme.colors.primary, borderWidth: 2 } });
