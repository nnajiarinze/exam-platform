describe('mobile API configuration',()=>{
  const previous={...process.env};
  afterEach(()=>{process.env={...previous};jest.resetModules()});
  it('uses the centralized canonical Swedish citizenship identifier',()=>{
    delete process.env.EXPO_PUBLIC_EXAM_ID;jest.resetModules();
    const {appConfig}=require('./config') as typeof import('./config');
    expect(appConfig.examId).toBe('swedish-citizenship');
  });
  it('uses the remote environment by default',()=>{
    const {appConfig}=require('./config') as typeof import('./config');
    const {CurrentEnvironment,Environment,environmentConfig}=require('../config/environment') as typeof import('../config/environment');
    expect(CurrentEnvironment).toBe(Environment.REMOTE);
    expect(appConfig.publicApiBaseUrl).toBe(environmentConfig.apiBaseUrl);
    expect(appConfig.learningBaseUrl).toBe(environmentConfig.learningBaseUrl);
    expect(appConfig.oidcIssuer).toBe(environmentConfig.oidcIssuer);
  });
  it('resolves both environments through the same URL builder',()=>{
    const {Environment,LocalGateway,LocalIdentity,resolveEnvironment}=require('../config/environment') as typeof import('../config/environment');
    const remote=resolveEnvironment(Environment.REMOTE);
    const local=resolveEnvironment(Environment.LOCAL);
    expect(remote.environment).toBe(Environment.REMOTE);
    expect(local.environment).toBe(Environment.LOCAL);
    expect(local.apiBaseUrl).toBe(LocalGateway.physicalDevice);
    expect(remote.learningBaseUrl).toBe(`${remote.apiBaseUrl}/learning`);
    expect(local.learningBaseUrl).toBe(LocalGateway.physicalDevice);
    expect(remote.oidcIssuer).toBe(`${remote.apiBaseUrl}/auth/realms/exam-platform`);
    expect(local.oidcIssuer).toBe(`${LocalIdentity.physicalDevice}/realms/exam-platform`);
  });
  it('prevents production builds from targeting the local backend',()=>{
    const {assertSafeEnvironment,Environment}=require('../config/environment') as typeof import('../config/environment');
    expect(()=>assertSafeEnvironment(Environment.LOCAL,'production')).toThrow(/Production mobile builds/);
    expect(()=>assertSafeEnvironment(Environment.REMOTE,'production')).not.toThrow();
  });
  it('joins endpoint paths without duplicate slashes',()=>{
    const {environmentConfig,joinBaseUrl}=require('../config/environment') as typeof import('../config/environment');
    expect(joinBaseUrl(`${environmentConfig.apiBaseUrl}/`,'/learning')).toBe(environmentConfig.learningBaseUrl);
  });
});
