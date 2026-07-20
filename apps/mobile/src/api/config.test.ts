describe('mobile exam configuration',()=>{
  const previous=process.env.EXPO_PUBLIC_EXAM_ID;
  afterEach(()=>{if(previous===undefined)delete process.env.EXPO_PUBLIC_EXAM_ID;else process.env.EXPO_PUBLIC_EXAM_ID=previous;jest.resetModules()});
  it('uses the centralized canonical Swedish citizenship identifier',()=>{
    delete process.env.EXPO_PUBLIC_EXAM_ID;jest.resetModules();
    const {appConfig}=require('./config') as typeof import('./config');
    expect(appConfig.examId).toBe('swedish-citizenship');
  });
});
