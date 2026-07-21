describe('mobile API configuration',()=>{
  const previous={...process.env};
  afterEach(()=>{process.env={...previous};jest.resetModules()});
  it('uses the centralized canonical Swedish citizenship identifier',()=>{
    delete process.env.EXPO_PUBLIC_EXAM_ID;jest.resetModules();
    const {appConfig}=require('./config') as typeof import('./config');
    expect(appConfig.examId).toBe('swedish-citizenship');
  });
  it('normalizes and routes a hosted gateway URL',()=>{
    process.env.EXPO_PUBLIC_API_BASE_URL='https://api.example.test/';delete process.env.EXPO_PUBLIC_OIDC_ISSUER;jest.resetModules();
    const {appConfig}=require('./config') as typeof import('./config');
    expect(appConfig.learningBaseUrl).toBe('https://api.example.test/learning');
    expect(appConfig.oidcIssuer).toBe('https://api.example.test/auth/realms/exam-platform');
  });
  it('keeps the direct learning URL available for local development',()=>{
    delete process.env.EXPO_PUBLIC_API_BASE_URL;process.env.EXPO_PUBLIC_LEARNING_BASE_URL='http://10.0.2.2:8080/';jest.resetModules();
    expect((require('./config') as typeof import('./config')).appConfig.learningBaseUrl).toBe('http://10.0.2.2:8080');
  });
  it('rejects localhost in production',()=>{
    process.env.EXPO_PUBLIC_APP_ENV='production';process.env.EXPO_PUBLIC_API_BASE_URL='http://localhost:8088';jest.resetModules();
    expect(()=>require('./config')).toThrow(/non-local/);
  });
  it('joins endpoint paths without duplicate slashes',()=>{
    const {joinBaseUrl}=require('./config') as typeof import('./config');
    expect(joinBaseUrl('https://api.example.test/','/learning')).toBe('https://api.example.test/learning');
  });
});
