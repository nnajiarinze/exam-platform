export enum Environment {
  LOCAL = 'LOCAL',
  REMOTE = 'REMOTE',
}

/** Set EXPO_PUBLIC_APP_ENV=LOCAL for Expo/LAN development; packaged builds default remote. */
export const CurrentEnvironment: Environment =
  process.env.EXPO_PUBLIC_APP_ENV === Environment.LOCAL ? Environment.LOCAL : Environment.REMOTE;

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

type EnvironmentConfig = {
  environment: Environment;
  apiBaseUrl: string;
  learningBaseUrl: string;
  authBaseUrl: string;
  oidcIssuer: string;
};

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
  configuredRemoteGateway = process.env.EXPO_PUBLIC_API_BASE_URL,
): EnvironmentConfig {
  const remoteGateway = configuredRemoteGateway?.trim();
  if (environment === Environment.REMOTE && !remoteGateway) {
    throw new Error('REMOTE mobile mode requires EXPO_PUBLIC_API_BASE_URL');
  }
  const apiBaseUrl = normalizeBaseUrl(
    environment === Environment.REMOTE ? remoteGateway! : LocalGateway.physicalDevice,
  );

  if (environment === Environment.LOCAL) {
    const authBaseUrl = normalizeBaseUrl(LocalIdentity.physicalDevice);
    return {
      environment,
      apiBaseUrl,
      learningBaseUrl: apiBaseUrl,
      authBaseUrl,
      oidcIssuer: joinBaseUrl(authBaseUrl, 'realms/exam-platform'),
    };
  }

  return {
    environment,
    apiBaseUrl,
    learningBaseUrl: joinBaseUrl(apiBaseUrl, 'learning'),
    authBaseUrl: joinBaseUrl(apiBaseUrl, 'auth'),
    oidcIssuer: joinBaseUrl(apiBaseUrl, 'auth/realms/exam-platform'),
  };
}

export const environmentConfig = resolveEnvironment(CurrentEnvironment);

export function assertSafeEnvironment(environment: Environment, nodeEnvironment = process.env.NODE_ENV): void {
  if (nodeEnvironment !== 'production') return;
  if (environment === Environment.LOCAL) throw new Error('Production mobile builds cannot use the LOCAL backend environment.');
  if (environmentConfig.apiBaseUrl.startsWith('http://')) throw new Error('Production mobile builds require an HTTPS API base URL.');
}

assertSafeEnvironment(CurrentEnvironment);

if (process.env.NODE_ENV !== 'test') {
  console.info(`[Environment] Environment: ${environmentConfig.environment}`);
  console.info(`[Environment] API: ${environmentConfig.apiBaseUrl}`);
}
