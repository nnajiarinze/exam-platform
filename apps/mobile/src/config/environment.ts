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

const RemoteGateway = 'https://citizenship-api-gateway.onrender.com';

type EnvironmentConfig = {
  environment: Environment;
  apiBaseUrl: string;
  learningBaseUrl: string;
  authBaseUrl: string;
  oidcIssuer: string;
  warmupUrls: readonly string[];
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

export function resolveEnvironment(environment: Environment): EnvironmentConfig {
  const apiBaseUrl = normalizeBaseUrl(
    environment === Environment.REMOTE ? RemoteGateway : LocalGateway.physicalDevice,
  );

  if (environment === Environment.LOCAL) {
    const authBaseUrl = normalizeBaseUrl(LocalIdentity.physicalDevice);
    return {
      environment,
      apiBaseUrl,
      learningBaseUrl: apiBaseUrl,
      authBaseUrl,
      oidcIssuer: joinBaseUrl(authBaseUrl, 'realms/exam-platform'),
      warmupUrls: [],
    };
  }

  return {
    environment,
    apiBaseUrl,
    learningBaseUrl: joinBaseUrl(apiBaseUrl, 'learning'),
    authBaseUrl: joinBaseUrl(apiBaseUrl, 'auth'),
    oidcIssuer: joinBaseUrl(apiBaseUrl, 'auth/realms/exam-platform'),
    warmupUrls: [
      joinBaseUrl(apiBaseUrl, 'auth/realms/exam-platform/.well-known/openid-configuration'),
      'https://citizenship-learning-service.onrender.com/actuator/health/readiness',
      'https://citizenship-content-service.onrender.com/actuator/health/readiness',
      'https://citizenship-ai-service.onrender.com/actuator/health/readiness',
    ],
  };
}

export const environmentConfig = resolveEnvironment(CurrentEnvironment);

export function assertSafeEnvironment(environment: Environment, nodeEnvironment = process.env.NODE_ENV): void {
  if (nodeEnvironment !== 'production' || environment !== Environment.LOCAL) return;
  throw new Error('Production mobile builds cannot use the LOCAL backend environment.');
}

assertSafeEnvironment(CurrentEnvironment);

if (process.env.NODE_ENV !== 'test') {
  console.info(`[Environment] Environment: ${environmentConfig.environment}`);
  console.info(`[Environment] API: ${environmentConfig.apiBaseUrl}`);
}
