import { Loading } from '../../components/ui';
import { Screen } from '../../components/Screen';

export function SplashScreen() {
  return <Screen scroll={false}><Loading label="Starting Medbo…" /></Screen>;
}
