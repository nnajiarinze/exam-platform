export enum Environment {
  LOCAL = 'LOCAL',
  HOSTED = 'HOSTED',
}

export const HOSTED_GATEWAY = 'https://api.tinkona.com';

export const LocalGateway = {
  physicalDevice: 'http://192.168.1.213:8080',
  iosSimulator: 'http://localhost:8080',
  androidEmulator: 'http://10.0.2.2:8080',
} as const;

export const LocalIdentity = {
  physicalDevice: 'http://192.168.1.213:8090',
  iosSimulator: 'http://localhost:8090',
  androidEmulator: 'http://10.0.2.2:8090',
} as const;

export type MobileEnvironmentConfig = {
  environment: Environment;
  displayLabel: string;
  apiBaseUrl: string;
  learningBaseUrl: string;
  authBaseUrl: string;
  oidcIssuer: string;
  cleartextAllowed: boolean;
  warning?: string;
};

export function parseEnvironment(value?: string): Environment {
  if (!value) return Environment.LOCAL;
  if (value === Environment.LOCAL || value === Environment.HOSTED) return value;
  throw new Error(`Unknown EXPO_PUBLIC_APP_ENV "${value}". Expected LOCAL or HOSTED.`);
}

export const CurrentEnvironment = parseEnvironment(process.env.EXPO_PUBLIC_APP_ENV);

export function normalizeBaseUrl(value: string): string {
  const normalized = value.trim().replace(/\/+$/, '');
  const parsed = new URL(normalized);
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('API base URL must use HTTP(S)');
  return normalized;
}

export function joinBaseUrl(baseUrl: string, path: string): string {
  return `${normalizeBaseUrl(baseUrl)}/${path.replace(/^\/+/, '')}`;
}

export function resolveEnvironment(
  environment: Environment,
  configuredGateway = process.env.EXPO_PUBLIC_API_BASE_URL,
): MobileEnvironmentConfig {
  if (environment === Environment.LOCAL) {
    const apiBaseUrl = normalizeBaseUrl(configuredGateway || LocalGateway.physicalDevice);
    const authBaseUrl = normalizeBaseUrl(process.env.EXPO_PUBLIC_LOCAL_IDENTITY_URL || LocalIdentity.physicalDevice);
    return {
      environment,
      displayLabel: 'Local',
      apiBaseUrl,
      learningBaseUrl: apiBaseUrl,
      authBaseUrl,
      oidcIssuer: joinBaseUrl(authBaseUrl, 'realms/exam-platform'),
      cleartextAllowed: true,
    };
  }
  const apiBaseUrl = normalizeBaseUrl(configuredGateway || HOSTED_GATEWAY);
  return {
    environment,
    displayLabel: 'Hosted',
    apiBaseUrl,
    learningBaseUrl: joinBaseUrl(apiBaseUrl, 'learning'),
    authBaseUrl: joinBaseUrl(apiBaseUrl, 'auth'),
    oidcIssuer: joinBaseUrl(apiBaseUrl, 'auth/realms/exam-platform'),
    cleartextAllowed: apiBaseUrl.startsWith('http://'),
    warning: apiBaseUrl.startsWith('http://') ? 'Hosted testing — insecure HTTP' : undefined,
  };
}

export function assertSafeEnvironment(
  config: MobileEnvironmentConfig,
  buildKind = process.env.EXPO_PUBLIC_BUILD_KIND || 'development',
): void {
  if (buildKind !== 'production') return;
  if (config.environment === Environment.LOCAL) {
    throw new Error('Production mobile builds cannot use the LOCAL backend environment.');
  }
  if (!config.apiBaseUrl.startsWith('https://')) {
    throw new Error('Production mobile builds require an HTTPS hosted API base URL.');
  }
}

export const environmentConfig = resolveEnvironment(CurrentEnvironment);
assertSafeEnvironment(environmentConfig);

if (process.env.NODE_ENV !== 'test') {
  console.info(`[Environment] Environment: ${environmentConfig.environment}`);
  console.info(`[Environment] API: ${environmentConfig.apiBaseUrl}`);
}
