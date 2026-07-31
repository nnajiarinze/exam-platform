import { QueryClient } from '@tanstack/react-query';
import { adminSessionStorageKey } from './authSession';
import { switchAdminEnvironment } from './environmentSwitch';
import { userManager } from './oidc';

describe('Admin environment switching', () => {
  it.each([['LOCAL','HOSTED'],['HOSTED','LOCAL']] as const)('clears %s auth and caches before selecting %s', async (_from,next) => {
    const localValues=new Map<string,string>();
    vi.stubGlobal('localStorage',{getItem:(key:string)=>localValues.get(key)??null,setItem:(key:string,value:string)=>localValues.set(key,value)});
    sessionStorage.setItem(adminSessionStorageKey,'local-development-identity');
    sessionStorage.setItem('oidc.LOCAL.user:test','local-token-state');
    sessionStorage.setItem('oidc.HOSTED.user:test','hosted-token-state');
    const queryClient=new QueryClient();
    queryClient.setQueryData(['environment-data'],{stale:true});
    const clear=vi.spyOn(queryClient,'clear');
    const removeUser=vi.spyOn(userManager,'removeUser').mockResolvedValue();
    const reload=vi.fn();

    await switchAdminEnvironment(next,queryClient,reload);

    expect(removeUser).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem(adminSessionStorageKey)).toBeNull();
    expect(sessionStorage.getItem('oidc.LOCAL.user:test')).toBeNull();
    expect(sessionStorage.getItem('oidc.HOSTED.user:test')).toBeNull();
    expect(clear).toHaveBeenCalledOnce();
    expect(localStorage.getItem('exam-platform.admin.environment')).toBe(next);
    expect(reload).toHaveBeenCalledOnce();
  });
});
