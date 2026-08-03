import { readEnvironment } from './environment';

describe('environment configuration', () => {
  it('uses the serving HTTPS origin for hosted API and OIDC discovery in production', () => {
    const originalLocation=globalThis.location;
    Object.defineProperty(globalThis,'location',{configurable:true,value:{origin:'https://api.tinkona.com'}});
    try {
      expect(readEnvironment({PROD:true,VITE_DEFAULT_APP_ENV:'HOSTED'})).toMatchObject({
        gatewayBaseUrl:'https://api.tinkona.com',
        contentServiceBaseUrl:'https://api.tinkona.com/content',
        oidcAuthority:'https://api.tinkona.com/auth/realms/exam-platform',
      });
    } finally {
      Object.defineProperty(globalThis,'location',{configurable:true,value:originalLocation});
    }
  });
  it('normalizes the service URL and development roles', () => {
    expect(readEnvironment({ VITE_DEFAULT_APP_ENV: 'LOCAL', VITE_CONTENT_SERVICE_BASE_URL: 'https://content.example/', VITE_DEV_ADMIN_AUTH_ENABLED: 'true', VITE_DEV_ADMIN_ID: 'a', VITE_DEV_ADMIN_NAME: 'Admin', VITE_DEV_ADMIN_ROLES: 'CONTENT_AUTHOR', VITE_DEV_REVIEWER_ID: 'r', VITE_DEV_REVIEWER_NAME: 'Reviewer', VITE_DEV_REVIEWER_ROLES: 'CONTENT_REVIEWER' })).toMatchObject({ contentServiceBaseUrl: 'https://content.example', developmentAdminRoles: ['CONTENT_AUTHOR'], developmentReviewerRoles: ['CONTENT_REVIEWER'] });
  });

  it('derives Content Service and OIDC routes from the public gateway', () => {
    expect(readEnvironment({ VITE_DEFAULT_APP_ENV:'HOSTED', VITE_API_BASE_URL: 'https://api.example.test/' })).toMatchObject({
      contentServiceBaseUrl: 'https://api.example.test/content',
      aiServiceBaseUrl: 'https://api.example.test/content/api/v1/admin/ai',
      learningServiceBaseUrl: 'https://api.example.test/content/api/v1/admin/reports',
      oidcAuthority: 'https://api.example.test/auth/realms/exam-platform',
      requiredScopes: ['openid', 'profile', 'email'],
    });
  });

  it('selects capability-specific hosted endpoints without changing local endpoints', () => {
    const source={VITE_DEFAULT_APP_ENV:'LOCAL',VITE_ENABLE_ENVIRONMENT_SWITCHER:'true',VITE_LOCAL_API_BASE_URL:'http://local.test',VITE_HOSTED_API_BASE_URL:'https://hosted.test',VITE_LOCAL_CONTENT_API_BASE_URL:'http://local.test/content',VITE_HOSTED_CONTENT_API_BASE_URL:'https://hosted.test/content',VITE_HOSTED_AI_API_BASE_URL:'https://hosted.test/content/api/v1/admin/ai',VITE_HOSTED_LEARNING_API_BASE_URL:'https://hosted.test/content/api/v1/admin/reports'};
    expect(readEnvironment(source)).toMatchObject({appEnvironment:'LOCAL',contentServiceBaseUrl:'http://local.test/content'});
    expect(readEnvironment(source,'HOSTED')).toMatchObject({appEnvironment:'HOSTED',contentServiceBaseUrl:'https://hosted.test/content',aiServiceBaseUrl:'https://hosted.test/content/api/v1/admin/ai',learningServiceBaseUrl:'https://hosted.test/content/api/v1/admin/reports'});
  });

  it('rejects localhost in production', () => {
    expect(() => readEnvironment({ PROD: true, VITE_DEFAULT_APP_ENV:'LOCAL', VITE_API_BASE_URL: 'http://localhost:8088' })).toThrow(/HOSTED/);
  });

  it('rejects invalid URLs', () => {
    expect(() => readEnvironment({ VITE_DEFAULT_APP_ENV:'LOCAL', VITE_CONTENT_SERVICE_BASE_URL: 'localhost:8081' })).toThrow(/absolute HTTP/);
  });

  it('honors valid stored selection only when the switcher is enabled', () => {
    expect(readEnvironment({ VITE_DEFAULT_APP_ENV:'LOCAL', VITE_ENABLE_ENVIRONMENT_SWITCHER:'true', VITE_DEV_ADMIN_AUTH_ENABLED:'true' }, 'HOSTED')).toMatchObject({
      appEnvironment:'HOSTED', contentServiceBaseUrl:'http://localhost:8088/content',
      oidcAuthority:'http://localhost:8088/auth/realms/exam-platform',
      warning:'Hosted testing — insecure HTTP', developmentAuthEnabled:false,
    });
    expect(readEnvironment({ VITE_DEFAULT_APP_ENV:'LOCAL', VITE_ENABLE_ENVIRONMENT_SWITCHER:'false' }, 'HOSTED').appEnvironment).toBe('LOCAL');
    expect(readEnvironment({ VITE_DEFAULT_APP_ENV:'LOCAL', VITE_ENABLE_ENVIRONMENT_SWITCHER:'true' }, 'CORRUPT').appEnvironment).toBe('LOCAL');
  });

  it('requires complete development identity configuration', () => {
    expect(() => readEnvironment({ VITE_DEFAULT_APP_ENV:'LOCAL', VITE_DEV_ADMIN_AUTH_ENABLED: 'true' })).toThrow(/requires an admin id/);
  });

  it('requires a complete reviewer identity when development auth is enabled', () => {
    expect(() => readEnvironment({ VITE_DEFAULT_APP_ENV:'LOCAL', VITE_DEV_ADMIN_AUTH_ENABLED: 'true', VITE_DEV_ADMIN_ID: 'a', VITE_DEV_ADMIN_NAME: 'Admin', VITE_DEV_ADMIN_ROLES: 'ADMIN' })).toThrow(/requires a reviewer id/);
  });

  it('never enables development authentication for HOSTED', () => {
    expect(readEnvironment({ VITE_DEFAULT_APP_ENV:'HOSTED', VITE_DEV_ADMIN_AUTH_ENABLED:'true' })).toMatchObject({
      developmentAuthEnabled:false,
      contentServiceBaseUrl:'http://localhost:8088/content',
      oidcAuthority:'http://localhost:8088/auth/realms/exam-platform',
    });
  });
});
