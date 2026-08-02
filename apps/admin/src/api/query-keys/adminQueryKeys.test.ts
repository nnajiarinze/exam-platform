import { adminQueryKeys } from './adminQueryKeys';
import { environment } from '../../app/config/environment';

describe('administrative query keys',()=>{
  it('namespaces cached authoring data by selected environment',()=>{
    expect(adminQueryKeys.sources.all[0]).toBe(environment.appEnvironment);
    expect(adminQueryKeys.facts.list({status:'APPROVED'})[0]).toBe(environment.appEnvironment);
    expect(adminQueryKeys.questions.all[0]).toBe(environment.appEnvironment);
    expect(adminQueryKeys.releases.all[0]).toBe(environment.appEnvironment);
    expect(adminQueryKeys.ai.providerStatus[0]).toBe(environment.appEnvironment);
  });
});
