describe('mobile API configuration',()=>{
  const previous={...process.env};
  afterEach(()=>{process.env={...previous};jest.resetModules()});
  beforeEach(()=>{process.env.EXPO_PUBLIC_API_BASE_URL='https://api.example.test'});
  it('uses the centralized canonical Swedish citizenship identifier',()=>{
    delete process.env.EXPO_PUBLIC_EXAM_ID;jest.resetModules();
    const {appConfig}=require('./config') as typeof import('./config');
    expect(appConfig.examId).toBe('swedish-citizenship');
  });
  it('uses the configured hosted environment',()=>{
    const {appConfig}=require('./config') as typeof import('./config');
    const {CurrentEnvironment,Environment,environmentConfig}=require('../config/environment') as typeof import('../config/environment');
    expect(CurrentEnvironment).toBe(Environment.HOSTED);
    expect(appConfig.publicApiBaseUrl).toBe(environmentConfig.apiBaseUrl);
    expect(appConfig.learningBaseUrl).toBe(environmentConfig.learningBaseUrl);
    expect(appConfig.oidcIssuer).toBe(environmentConfig.oidcIssuer);
  });
  it('resolves both environments through the same URL builder',()=>{
    const {Environment,LocalGateway,LocalIdentity,resolveEnvironment}=require('../config/environment') as typeof import('../config/environment');
    const remote=resolveEnvironment(Environment.HOSTED);
    const local=resolveEnvironment(Environment.LOCAL,LocalGateway.physicalDevice);
    expect(remote.environment).toBe(Environment.HOSTED);
    expect(local.environment).toBe(Environment.LOCAL);
    expect(local.apiBaseUrl).toBe(LocalGateway.physicalDevice);
    expect(remote.learningBaseUrl).toBe(`${remote.apiBaseUrl}/learning`);
    expect(local.learningBaseUrl).toBe(LocalGateway.physicalDevice);
    expect(remote.oidcIssuer).toBe(`${remote.apiBaseUrl}/auth/realms/exam-platform`);
    expect(local.oidcIssuer).toBe(`${LocalIdentity.physicalDevice}/realms/exam-platform`);
  });
  it('prevents production builds from targeting the local backend',()=>{
    const {assertSafeEnvironment,Environment,resolveEnvironment}=require('../config/environment') as typeof import('../config/environment');
    expect(()=>assertSafeEnvironment(resolveEnvironment(Environment.LOCAL),'production')).toThrow(/Production mobile builds/);
    expect(()=>assertSafeEnvironment(resolveEnvironment(Environment.HOSTED,'http://46.224.221.7'),'production')).toThrow(/HTTPS/);
    expect(()=>assertSafeEnvironment(resolveEnvironment(Environment.HOSTED,'https://api.example.test'),'production')).not.toThrow();
  });
  it('uses the central hosted default and rejects invalid environment names',()=>{
    const {Environment,HOSTED_GATEWAY,parseEnvironment,resolveEnvironment}=require('../config/environment') as typeof import('../config/environment');
    expect(resolveEnvironment(Environment.HOSTED,'').apiBaseUrl).toBe(HOSTED_GATEWAY);
    expect(()=>parseEnvironment('REMOTE')).toThrow(/Unknown/);
  });
  it('joins endpoint paths without duplicate slashes',()=>{
    const {environmentConfig,joinBaseUrl}=require('../config/environment') as typeof import('../config/environment');
    expect(joinBaseUrl(`${environmentConfig.apiBaseUrl}/`,'/learning')).toBe(environmentConfig.learningBaseUrl);
  });
});
